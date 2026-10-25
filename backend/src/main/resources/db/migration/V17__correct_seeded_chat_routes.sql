-- The configured chat endpoint is Groq. Repair only the known untouched seeds;
-- preserve administrator-created routes and changes to seed weights/activation.
UPDATE model_versions
SET model_path = 'llama-3.1-8b-instant'
WHERE version_name = 'base-llama-3.1'
  AND model_path = 'groq/llama-3.1-8b-instant';

UPDATE model_versions
SET is_active = FALSE, traffic_weight = 0
WHERE version_name = 'gpt-4o-mini-default'
  AND model_path = 'gpt-4o-mini'
  AND is_active = TRUE AND traffic_weight = 100;
