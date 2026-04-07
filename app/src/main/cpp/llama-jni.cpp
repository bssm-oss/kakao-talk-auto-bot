#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <android/log.h>

#include <llama.h>

#define LOG_TAG "llama-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct ModelState {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    llama_sampler *sampler = nullptr;
    int32_t n_past = 0;
};

static ModelState g_state;
static bool g_initialized = false;

// Manual batch management (llama_batch_clear/add removed in new API)
struct SimpleBatch {
    int32_t n_tokens = 0;
    std::vector<llama_token> tokens;
    std::vector<llama_pos> pos;
    std::vector<int32_t> n_seq_id;
    std::vector<llama_seq_id> seq_id_storage; // actual seq_id data
    std::vector<llama_seq_id*> seq_id_ptrs;   // pointers to seq_id data
    std::vector<int8_t> logits;

    SimpleBatch(int32_t capacity) {
        tokens.reserve(capacity);
        pos.reserve(capacity);
        n_seq_id.reserve(capacity);
        seq_id_ptrs.reserve(capacity);
        logits.reserve(capacity);
    }

    void clear() {
        n_tokens = 0;
        tokens.clear();
        pos.clear();
        n_seq_id.clear();
        seq_id_storage.clear();
        seq_id_ptrs.clear();
        logits.clear();
    }

    void add(llama_token token, llama_pos p, bool logit) {
        tokens.push_back(token);
        pos.push_back(p);
        n_seq_id.push_back(1);
        seq_id_storage.push_back(0); // seq_id = 0 for all tokens
        seq_id_ptrs.push_back(&seq_id_storage.back());
        logits.push_back(logit ? 1 : 0);
        n_tokens++;
    }

    llama_batch to_llama_batch() {
        llama_batch batch;
        batch.n_tokens = n_tokens;
        batch.token = tokens.data();
        batch.pos = pos.data();
        batch.n_seq_id = n_seq_id.data();
        batch.seq_id = seq_id_ptrs.data();
        batch.logits = logits.data();
        return batch;
    }
};

static SimpleBatch *g_batch = nullptr;

static llama_sampler *create_sampler(const llama_model *model, float temperature, float top_p, int32_t top_k) {
    const auto *vocab = llama_model_get_vocab(model);
    (void)vocab;

    // Create a sampler chain with greedy/top-k/top-p
    llama_sampler_chain_params chain_params = llama_sampler_chain_default_params();

    llama_sampler *chain = llama_sampler_chain_init(chain_params);

    // Add temperature-based sampler
    if (temperature <= 0.0f) {
        // Greedy
        llama_sampler_chain_add(chain, llama_sampler_init_greedy());
    } else {
        // Temperature + top-k + top-p
        llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
        if (top_k > 0) {
            llama_sampler_chain_add(chain, llama_sampler_init_top_k(top_k));
        }
        if (top_p > 0.0f && top_p < 1.0f) {
            llama_sampler_chain_add(chain, llama_sampler_init_top_p(top_p, 1));
        }
        llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    return chain;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_kakaotalkautobot_LlmEngine_nativeInit(JNIEnv *env, jobject /*thiz*/,
                                                        jstring modelPathJStr,
                                                        jint /*maxTokens*/,
                                                        jfloat temperature,
                                                        jfloat topP,
                                                        jint topK,
                                                        jint nCtx) {
    if (g_initialized) {
        LOGI("Model already initialized, freeing first");
        if (g_state.sampler) llama_sampler_free(g_state.sampler);
        if (g_state.ctx) llama_free(g_state.ctx);
        if (g_state.model) llama_model_free(g_state.model);
        if (g_batch) delete g_batch;
        g_state = {};
        g_batch = nullptr;
    }

    const char *modelPath = env->GetStringUTFChars(modelPathJStr, nullptr);
    if (!modelPath) {
        LOGE("Failed to get model path");
        return JNI_FALSE;
    }

    LOGI("Loading model: %s", modelPath);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU only

    g_state.model = llama_model_load_from_file(modelPath, mparams);
    env->ReleaseStringUTFChars(modelPathJStr, modelPath);

    if (!g_state.model) {
        LOGE("Failed to load model");
        llama_backend_free();
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx > 0 ? nCtx : 2048;
    cparams.n_batch = 512;
    cparams.no_perf = true;

    g_state.ctx = llama_init_from_model(g_state.model, cparams);
    if (!g_state.ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_state.model);
        llama_backend_free();
        return JNI_FALSE;
    }

    g_state.sampler = create_sampler(g_state.model, temperature, topP, topK);
    if (!g_state.sampler) {
        LOGE("Failed to create sampler");
        llama_free(g_state.ctx);
        llama_model_free(g_state.model);
        llama_backend_free();
        return JNI_FALSE;
    }

    g_batch = new SimpleBatch(512);
    g_state.n_past = 0;
    g_initialized = true;

    const auto *vocab = llama_model_get_vocab(g_state.model);
    int32_t n_vocab = llama_vocab_n_tokens(vocab);
    LOGI("Model loaded: n_vocab=%d, n_ctx=%d", n_vocab, nCtx);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_kakaotalkautobot_LlmEngine_nativeGenerate(JNIEnv *env, jobject /*thiz*/,
                                                            jstring promptJStr,
                                                            jint maxTokens) {
    if (!g_initialized || !g_state.ctx || !g_state.model || !g_state.sampler) {
        LOGE("Model not initialized");
        return env->NewStringUTF("");
    }

    const char *promptCStr = env->GetStringUTFChars(promptJStr, nullptr);
    if (!promptCStr) {
        return env->NewStringUTF("");
    }

    std::string prompt(promptCStr);
    env->ReleaseStringUTFChars(promptJStr, promptCStr);

    const auto *vocab = llama_model_get_vocab(g_state.model);

    // Tokenize
    std::vector<llama_token> tokens(prompt.size() + 16);
    int32_t n_tokens = llama_tokenize(vocab, prompt.data(), (int32_t)prompt.size(),
                                       tokens.data(), (int32_t)tokens.size(), true, true);

    if (n_tokens < 0) {
        LOGE("Tokenization failed, retrying without BOS");
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, prompt.data(), (int32_t)prompt.size(),
                                   tokens.data(), (int32_t)tokens.size(), false, true);
        if (n_tokens < 0) {
            LOGE("Tokenization still failed");
            return env->NewStringUTF("");
        }
    }

    LOGI("Prompt tokens: %d", n_tokens);

    llama_sampler_reset(g_state.sampler);

    // Prefill
    const int32_t n_ctx = llama_n_ctx(g_state.ctx);
    const int32_t n_batch = llama_n_batch(g_state.ctx);
    int32_t n_eval = 0;

    while (n_tokens > 0) {
        int32_t batch_len = std::min(n_tokens, n_batch);

        // Handle context overflow
        if (n_eval + batch_len > n_ctx) {
            const int32_t n_keep = 4;
            const int32_t n_discard = n_ctx - n_keep - batch_len;
            llama_kv_self_seq_rm(g_state.ctx, -1, n_keep, n_keep + n_discard);
            llama_kv_self_seq_add(g_state.ctx, -1, n_keep + n_discard, n_eval, -n_discard);
            n_eval -= n_discard;
        }

        g_batch->clear();
        for (int32_t i = 0; i < batch_len; i++) {
            bool is_last = (i == batch_len - 1) && (n_tokens == batch_len);
            g_batch->add(tokens[i], n_eval + i, is_last);
        }

        llama_batch lb = g_batch->to_llama_batch();
        if (llama_decode(g_state.ctx, lb) != 0) {
            LOGE("llama_decode failed during prefill");
            return env->NewStringUTF("");
        }

        n_eval += batch_len;
        n_tokens -= batch_len;
        tokens.erase(tokens.begin(), tokens.begin() + batch_len);
    }

    g_state.n_past = n_eval;

    // Generation (stop on length or empty output)
    std::string result;
    int consecutive_empty = 0;

    for (int i = 0; i < maxTokens; i++) {
        llama_sampler_accept(g_state.sampler, -1);
        llama_token new_token = llama_sampler_sample(g_state.sampler, g_state.ctx, -1);

        // Decode token to text
        char buf[32];
        int32_t n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);

        if (n <= 0) {
            consecutive_empty++;
            if (consecutive_empty >= 3 && !result.empty()) {
                LOGI("Empty tokens detected, stopping after %d tokens", i);
                break;
            }
            // Still feed back for context
        } else {
            consecutive_empty = 0;
            buf[n] = '\0';
            result += buf;
        }

        // Feed back for next iteration
        g_batch->clear();
        g_batch->add(new_token, g_state.n_past, true);
        g_state.n_past++;

        llama_batch lb = g_batch->to_llama_batch();
        if (llama_decode(g_state.ctx, lb) != 0) {
            LOGE("llama_decode failed during generation");
            break;
        }

        if (result.size() > 4000) {
            LOGI("Response truncated due to length");
            break;
        }
    }

    LOGI("Generated %zu bytes", result.size());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_kakaotalkautobot_LlmEngine_nativeFree(JNIEnv *env, jobject /*thiz*/) {
    if (g_state.sampler) {
        llama_sampler_free(g_state.sampler);
        g_state.sampler = nullptr;
    }
    if (g_state.ctx) {
        llama_free(g_state.ctx);
        g_state.ctx = nullptr;
    }
    if (g_state.model) {
        llama_model_free(g_state.model);
        g_state.model = nullptr;
    }
    if (g_batch) {
        delete g_batch;
        g_batch = nullptr;
    }
    llama_backend_free();
    g_initialized = false;
    LOGI("Model freed");
}
