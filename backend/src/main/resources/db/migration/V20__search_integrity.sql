-- Preserve historical jobs/applications and their references. Only one existing row claims each key.
CREATE FUNCTION job_identity(url text, title text, company text, location text, body text)
RETURNS text LANGUAGE sql IMMUTABLE AS $$
 SELECT CASE WHEN nullif(btrim(url),'') IS NOT NULL AND btrim(url) <> '#'
   THEN 'url:' || encode(sha256(convert_to(btrim(url),'UTF8')),'hex')
   ELSE 'text:' || encode(sha256(convert_to(jsonb_build_array(title,company,location,body)::text,'UTF8')),'hex') END
$$;
ALTER TABLE jobs ADD COLUMN dedup_key text;
WITH ranked AS (
 SELECT id, job_identity(source_url,title,company,location,raw_text) AS key,
 row_number() OVER (PARTITION BY user_id,job_identity(source_url,title,company,location,raw_text) ORDER BY created_at,id) AS n FROM jobs
) UPDATE jobs j SET dedup_key=r.key FROM ranked r WHERE r.id=j.id AND r.n=1;
CREATE UNIQUE INDEX jobs_owner_identity ON jobs(user_id,dedup_key);
CREATE FUNCTION assign_job_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' OR NEW.user_id IS DISTINCT FROM OLD.user_id OR
   job_identity(NEW.source_url,NEW.title,NEW.company,NEW.location,NEW.raw_text) IS DISTINCT FROM
   job_identity(OLD.source_url,OLD.title,OLD.company,OLD.location,OLD.raw_text) THEN
   NEW.dedup_key=job_identity(NEW.source_url,NEW.title,NEW.company,NEW.location,NEW.raw_text);
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER jobs_identity BEFORE INSERT OR UPDATE ON jobs FOR EACH ROW EXECUTE FUNCTION assign_job_identity();

ALTER TABLE applications ADD COLUMN dedup_job_id uuid;
WITH ranked AS (
 SELECT id,job_id,row_number() OVER(PARTITION BY user_id,job_id ORDER BY created_at,id) AS n
 FROM applications WHERE job_id IS NOT NULL
) UPDATE applications a SET dedup_job_id=r.job_id FROM ranked r WHERE r.id=a.id AND r.n=1;
CREATE UNIQUE INDEX applications_owner_job ON applications(user_id,dedup_job_id);
CREATE FUNCTION assign_application_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' OR (NEW.user_id,NEW.job_id) IS DISTINCT FROM (OLD.user_id,OLD.job_id) THEN
   NEW.dedup_job_id=NEW.job_id;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER applications_identity BEFORE INSERT OR UPDATE ON applications FOR EACH ROW EXECUTE FUNCTION assign_application_identity();

-- Durable embedding delivery: source changes and queue changes commit or roll back together.
CREATE TABLE embedding_tasks (
 kind text NOT NULL CHECK(kind IN ('JOB','RESUME')),
 entity_id uuid NOT NULL,
 generation bigint NOT NULL DEFAULT 1,
 status text NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
 attempts integer NOT NULL DEFAULT 0,
 token uuid,
 lease_until timestamptz,
 available_at timestamptz NOT NULL DEFAULT now(),
 last_error text,
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(kind,entity_id)
);
CREATE INDEX embedding_tasks_ready ON embedding_tasks(available_at) WHERE status IN ('PENDING','FAILED','RUNNING');
CREATE FUNCTION enqueue_embedding() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE entity_kind text := TG_ARGV[0];
BEGIN
 IF TG_OP='DELETE' THEN
   DELETE FROM embedding_tasks WHERE kind=entity_kind AND entity_id=OLD.id;
   RETURN OLD;
 END IF;
 IF TG_OP='INSERT' OR NEW.raw_text IS DISTINCT FROM OLD.raw_text THEN
   INSERT INTO embedding_tasks(kind,entity_id) VALUES(entity_kind,NEW.id)
   ON CONFLICT(kind,entity_id) DO UPDATE SET generation=embedding_tasks.generation+1,
    status='PENDING',attempts=0,token=NULL,lease_until=NULL,available_at=now(),last_error=NULL,updated_at=now();
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER jobs_enqueue_embedding AFTER INSERT OR UPDATE OR DELETE ON jobs
FOR EACH ROW EXECUTE FUNCTION enqueue_embedding('JOB');
CREATE TRIGGER resumes_enqueue_embedding AFTER INSERT OR UPDATE OR DELETE ON resumes
FOR EACH ROW EXECUTE FUNCTION enqueue_embedding('RESUME');
-- Rebuild from source because legacy vectors used inconsistent inputs/stores.
INSERT INTO embedding_tasks(kind,entity_id) SELECT 'JOB',id FROM jobs;
INSERT INTO embedding_tasks(kind,entity_id) SELECT 'RESUME',id FROM resumes;
-- Remove only redundant derived vector rows; source records and application history remain intact.
DELETE FROM job_embeddings a USING job_embeddings b WHERE a.job_id=b.job_id AND (a.created_at,a.id)<(b.created_at,b.id);
CREATE UNIQUE INDEX job_embeddings_one_per_job ON job_embeddings(job_id);

-- If a canonical historical row is removed, let a retained duplicate inherit its claim.
CREATE FUNCTION promote_job_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.dedup_key IS NOT NULL AND EXISTS(SELECT 1 FROM users WHERE id=OLD.user_id) THEN
   UPDATE jobs SET dedup_key=OLD.dedup_key WHERE id=(SELECT id FROM jobs
     WHERE user_id=OLD.user_id AND dedup_key IS NULL AND
       job_identity(source_url,title,company,location,raw_text)=OLD.dedup_key
     ORDER BY created_at,id LIMIT 1);
 END IF;
 RETURN OLD;
END $$;
CREATE TRIGGER jobs_promote_identity AFTER DELETE ON jobs FOR EACH ROW EXECUTE FUNCTION promote_job_identity();
CREATE FUNCTION promote_application_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.dedup_job_id IS NOT NULL AND EXISTS(SELECT 1 FROM users WHERE id=OLD.user_id) THEN
   UPDATE applications SET dedup_job_id=OLD.dedup_job_id WHERE id=(SELECT id FROM applications
     WHERE user_id=OLD.user_id AND job_id=OLD.dedup_job_id AND dedup_job_id IS NULL ORDER BY created_at,id LIMIT 1);
 END IF;
 RETURN OLD;
END $$;
CREATE TRIGGER applications_promote_identity AFTER DELETE ON applications FOR EACH ROW EXECUTE FUNCTION promote_application_identity();
