-- V6: Fix Ollama model tags to use proper Ollama library names with size tags.
-- Previously model IDs like 'gemma4' or 'gemma3:4b' were used without correct tags.
-- This migration normalises everything to gemma4:e2b (Gemma 4 E2B, 7.2GB).

-- Fix user agent configs
UPDATE user_agent_configs SET model = 'gemma4:e2b',     description = 'Gemma 4 E2B (local)'        WHERE model IN ('gemma4', 'gemma-4', 'gemma3:4b', 'gemma3') AND provider = 'ollama';
UPDATE user_agent_configs SET model = 'qwen2.5-coder:7b', description = 'Qwen 2.5 Coder 7B (local)' WHERE model = 'qwen2.5-coder' AND provider = 'ollama';
UPDATE user_agent_configs SET model = 'llama3.3:latest'   WHERE model = 'llama3.3'         AND provider = 'ollama';
UPDATE user_agent_configs SET model = 'deepseek-coder-v2:16b' WHERE model = 'deepseek-coder-v2' AND provider = 'ollama';
UPDATE user_agent_configs SET model = 'codestral:latest'  WHERE model = 'codestral'        AND provider = 'ollama';

-- Fix test generator configs
UPDATE agent_test_configs SET generator_model = 'gemma4:e2b' WHERE generator_model IN ('gemma4', 'gemma-4', 'gemma3:4b', 'gemma3') AND generator_provider = 'ollama';
UPDATE agent_test_configs SET generator_model = 'qwen2.5-coder:7b' WHERE generator_model = 'qwen2.5-coder' AND generator_provider = 'ollama';
