#include <jni.h>
#include <string>
#include <thread>
#include <vector>
#include <algorithm>
#include <cstdlib>
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

static int32_t choose_thread_count() {
    const uint32_t detected = std::thread::hardware_concurrency();
    const long online = sysconf(_SC_NPROCESSORS_ONLN);
    const int32_t available = std::max(
        detected > 0 ? static_cast<int32_t>(detected) : 0,
        online > 0 ? static_cast<int32_t>(online) : 0
    );
    return std::max(4, std::min(available, 8));
}

static std::string sanitize_utf8_for_jni(const std::string &input) {
    std::string output;
    output.reserve(input.size());

    for (size_t i = 0; i < input.size();) {
        const unsigned char c = static_cast<unsigned char>(input[i]);

        if (c == 0x00) {
            output.push_back(' ');
            ++i;
            continue;
        }

        if (c <= 0x7F) {
            output.push_back(static_cast<char>(c));
            ++i;
            continue;
        }

        auto is_cont = [&](size_t idx) {
            return idx < input.size() && (static_cast<unsigned char>(input[idx]) & 0xC0) == 0x80;
        };

        if ((c & 0xE0) == 0xC0) {
            if (i + 1 < input.size() && is_cont(i + 1)) {
                output.append(input, i, 2);
                i += 2;
            } else {
                output.push_back('?');
                ++i;
            }
            continue;
        }

        if ((c & 0xF0) == 0xE0) {
            if (i + 2 < input.size() && is_cont(i + 1) && is_cont(i + 2)) {
                output.append(input, i, 3);
                i += 3;
            } else {
                output.push_back('?');
                ++i;
            }
            continue;
        }

        if ((c & 0xF8) == 0xF0) {
            if (i + 3 < input.size() && is_cont(i + 1) && is_cont(i + 2) && is_cont(i + 3)) {
                output.append(input, i, 4);
                i += 4;
            } else {
                output.push_back('?');
                ++i;
            }
            continue;
        }

        output.push_back('?');
        ++i;
    }

    return output;
}

static std::u16string utf8_to_utf16_lossy(const std::string &input) {
    std::u16string output;
    output.reserve(input.size());

    for (size_t i = 0; i < input.size();) {
        const unsigned char c = static_cast<unsigned char>(input[i]);

        if (c <= 0x7F) {
            output.push_back(static_cast<char16_t>(c));
            ++i;
            continue;
        }

        auto continuation = [&](size_t idx) {
            return idx < input.size() && (static_cast<unsigned char>(input[idx]) & 0xC0) == 0x80;
        };

        uint32_t codepoint = 0;
        size_t advance = 0;

        if ((c & 0xE0) == 0xC0 && continuation(i + 1)) {
            codepoint = ((c & 0x1F) << 6) |
                        (static_cast<unsigned char>(input[i + 1]) & 0x3F);
            advance = 2;
        } else if ((c & 0xF0) == 0xE0 && continuation(i + 1) && continuation(i + 2)) {
            codepoint = ((c & 0x0F) << 12) |
                        ((static_cast<unsigned char>(input[i + 1]) & 0x3F) << 6) |
                        (static_cast<unsigned char>(input[i + 2]) & 0x3F);
            advance = 3;
        } else if ((c & 0xF8) == 0xF0 && continuation(i + 1) && continuation(i + 2) && continuation(i + 3)) {
            codepoint = ((c & 0x07) << 18) |
                        ((static_cast<unsigned char>(input[i + 1]) & 0x3F) << 12) |
                        ((static_cast<unsigned char>(input[i + 2]) & 0x3F) << 6) |
                        (static_cast<unsigned char>(input[i + 3]) & 0x3F);
            advance = 4;
        }

        if (advance == 0 || codepoint == 0 || codepoint > 0x10FFFF || (codepoint >= 0xD800 && codepoint <= 0xDFFF)) {
            output.push_back(u'?');
            ++i;
            continue;
        }

        if (codepoint <= 0xFFFF) {
            output.push_back(static_cast<char16_t>(codepoint));
        } else {
            codepoint -= 0x10000;
            output.push_back(static_cast<char16_t>(0xD800 + (codepoint >> 10)));
            output.push_back(static_cast<char16_t>(0xDC00 + (codepoint & 0x3FF)));
        }

        i += advance;
    }

    return output;
}

static llama_batch *new_batch(int32_t n_tokens, int32_t embd, int32_t n_seq_max) {
    llama_batch *batch = new llama_batch {
        0,
        nullptr,
        nullptr,
        nullptr,
        nullptr,
        nullptr,
        nullptr,
    };

    if (embd) {
        batch->embd = static_cast<float *>(malloc(sizeof(float) * n_tokens * embd));
    } else {
        batch->token = static_cast<llama_token *>(malloc(sizeof(llama_token) * n_tokens));
    }

    batch->pos      = static_cast<llama_pos *>(malloc(sizeof(llama_pos) * n_tokens));
    batch->n_seq_id = static_cast<int32_t *>(malloc(sizeof(int32_t) * n_tokens));
    batch->seq_id   = static_cast<llama_seq_id **>(malloc(sizeof(llama_seq_id *) * n_tokens));
    for (int i = 0; i < n_tokens; ++i) {
        batch->seq_id[i] = static_cast<llama_seq_id *>(malloc(sizeof(llama_seq_id) * n_seq_max));
    }
    batch->logits = static_cast<int8_t *>(malloc(sizeof(int8_t) * n_tokens));

    return batch;
}

static void free_batch(llama_batch *batch, int32_t n_tokens) {
    if (!batch) return;
    if (batch->seq_id) {
        for (int i = 0; i < n_tokens; ++i) {
            free(batch->seq_id[i]);
        }
    }
    free(batch->token);
    free(batch->embd);
    free(batch->pos);
    free(batch->n_seq_id);
    free(batch->seq_id);
    free(batch->logits);
    delete batch;
}

static void batch_clear(llama_batch &batch) {
    batch.n_tokens = 0;
}

static void batch_add(llama_batch &batch, llama_token token, llama_pos pos, bool logits) {
    batch.token[batch.n_tokens] = token;
    batch.pos[batch.n_tokens] = pos;
    batch.n_seq_id[batch.n_tokens] = 1;
    batch.seq_id[batch.n_tokens][0] = 0;
    batch.logits[batch.n_tokens] = logits ? 1 : 0;
    batch.n_tokens++;
}

static llama_batch *g_batch = nullptr;
static int32_t g_batch_capacity = 0;

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
        if (g_batch) free_batch(g_batch, g_batch_capacity);
        g_state = {};
        g_batch = nullptr;
        g_batch_capacity = 0;
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
    cparams.n_batch = std::min<uint32_t>(32u, cparams.n_ctx);
    const int32_t cpu_threads = choose_thread_count();
    cparams.n_threads = cpu_threads;
    cparams.n_threads_batch = cpu_threads;
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

    g_batch_capacity = std::max<int32_t>(512, cparams.n_ctx);
    g_batch = new_batch(g_batch_capacity, 0, 1);
    g_state.n_past = 0;
    g_initialized = true;

    const auto *vocab = llama_model_get_vocab(g_state.model);
    int32_t n_vocab = llama_vocab_n_tokens(vocab);
    LOGI("Model loaded: n_vocab=%d, n_ctx=%d, n_batch=%u, n_threads=%d", n_vocab, nCtx, cparams.n_batch, cpu_threads);
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

    const int32_t n_ctx = llama_n_ctx(g_state.ctx);
    const int32_t max_prompt_tokens = std::max(32, n_ctx - std::min<int32_t>(maxTokens + 64, 128));
    if (n_tokens > max_prompt_tokens) {
        const int32_t skip = n_tokens - max_prompt_tokens;
        tokens.erase(tokens.begin(), tokens.begin() + skip);
        n_tokens = static_cast<int32_t>(tokens.size());
        LOGI("Prompt truncated from %d to %d tokens to fit context", n_tokens + skip, n_tokens);
    }

    LOGI("Prompt tokens: %d", n_tokens);

    llama_sampler_reset(g_state.sampler);
    llama_kv_self_clear(g_state.ctx);
    g_state.n_past = 0;

    // Prefill
    const int32_t n_batch = std::min<int32_t>(llama_n_batch(g_state.ctx), 32);
    int32_t n_eval = 0;

    while (n_tokens > 0) {
        int32_t batch_len = std::min(n_tokens, n_batch);

        batch_clear(*g_batch);
        for (int32_t i = 0; i < batch_len; i++) {
            bool is_last = (i == batch_len - 1) && (n_tokens == batch_len);
            batch_add(*g_batch, tokens[i], n_eval + i, is_last);
        }

        if (llama_decode(g_state.ctx, *g_batch) != 0) {
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
        llama_token new_token = llama_sampler_sample(g_state.sampler, g_state.ctx, -1);
        llama_sampler_accept(g_state.sampler, new_token);

        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }

        // Decode token to text
        std::vector<char> piece(128);
        int32_t n = llama_token_to_piece(vocab, new_token, piece.data(), static_cast<int32_t>(piece.size()), 0, true);
        if (n > static_cast<int32_t>(piece.size())) {
            piece.resize(n);
            n = llama_token_to_piece(vocab, new_token, piece.data(), static_cast<int32_t>(piece.size()), 0, true);
        }

        if (n <= 0) {
            consecutive_empty++;
            if (consecutive_empty >= 3 && !result.empty()) {
                LOGI("Empty tokens detected, stopping after %d tokens", i);
                break;
            }
            // Still feed back for context
        } else {
            consecutive_empty = 0;
            result.append(piece.data(), n);
        }

        // Feed back for next iteration
        batch_clear(*g_batch);
        batch_add(*g_batch, new_token, g_state.n_past, true);
        g_state.n_past++;

        if (llama_decode(g_state.ctx, *g_batch) != 0) {
            LOGE("llama_decode failed during generation");
            break;
        }

        if (result.size() > 4000) {
            LOGI("Response truncated due to length");
            break;
        }
    }

    std::string sanitized = sanitize_utf8_for_jni(result);
    std::u16string utf16 = utf8_to_utf16_lossy(sanitized);
    LOGI("Generated %zu bytes (%zu bytes sanitized, %zu utf16 units)", result.size(), sanitized.size(), utf16.size());
    return env->NewString(reinterpret_cast<const jchar *>(utf16.data()), static_cast<jsize>(utf16.size()));
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
        free_batch(g_batch, g_batch_capacity);
        g_batch = nullptr;
        g_batch_capacity = 0;
    }
    llama_backend_free();
    g_initialized = false;
    LOGI("Model freed");
}
