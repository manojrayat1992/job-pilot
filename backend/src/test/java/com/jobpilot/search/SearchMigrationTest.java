package com.jobpilot.search;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** An isolated upgrade database inside the disposable test server; never a project database. */
@EnabledIfEnvironmentVariable(named="JOBPILOT_REVIEW_DB_URL",matches=".+")
class SearchMigrationTest {
    @Test void upgradingPopulatedDatabasePreservesDuplicateJobsApplicationsAndReferences() {
        String root=System.getenv("JOBPILOT_REVIEW_DB_URL");
        var admin=new JdbcTemplate(new DriverManagerDataSource(root,"review_test","local_review_test_only"));
        String database="search_upgrade_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE DATABASE "+database);
        try {
            var ds=new DriverManagerDataSource(root.replaceFirst("/[^/?]+(\\?.*)?$","/"+database),"review_test","local_review_test_only");
            Flyway.configure().dataSource(ds).target("19").locations("classpath:db/migration").load().migrate();
            var jdbc=new JdbcTemplate(ds);UUID user=UUID.randomUUID(),job1=UUID.randomUUID(),job2=UUID.randomUUID(),app1=UUID.randomUUID(),app2=UUID.randomUUID();
            jdbc.update("INSERT INTO users(id,email,password_hash,full_name) VALUES(?,?,?,?)",user,"upgrade@test.example","test-only","Test");
            for(UUID job:java.util.List.of(job1,job2))jdbc.update("INSERT INTO jobs(id,user_id,title,source_url,raw_text) VALUES(?,?,?,?,?)",job,user,"Title","https://example.test/same","Original "+job);
            jdbc.update("INSERT INTO applications(id,user_id,job_id,company,position,notes,status) VALUES(?,?,?,?,?,?,?)",app1,user,job1,"Company","Title","First history","APPLIED");
            jdbc.update("INSERT INTO applications(id,user_id,job_id,company,position,notes,status) VALUES(?,?,?,?,?,?,?)",app2,user,job1,"Company","Title","Second history","INTERVIEW");
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM jobs",Integer.class));
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM applications WHERE job_id=?",Integer.class,job1));
            assertEquals("Second history",jdbc.queryForObject("SELECT notes FROM applications WHERE id=?",String.class,app2));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM jobs WHERE dedup_key IS NOT NULL",Integer.class));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM applications WHERE dedup_job_id IS NOT NULL",Integer.class));
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM embedding_tasks WHERE status='PENDING'",Integer.class));
            // Editing a retained duplicate does not accidentally claim an already-owned URL.
            jdbc.update("UPDATE jobs SET title='Edited',raw_text='Updated content' WHERE dedup_key IS NULL");
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO jobs(user_id,source_url,raw_text) VALUES(?,?,?)",user,"https://example.test/same","duplicate"));
            UUID canonical=jdbc.queryForObject("SELECT id FROM jobs WHERE dedup_key IS NOT NULL",UUID.class);
            jdbc.update("DELETE FROM jobs WHERE id=?",canonical);
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM jobs WHERE dedup_key IS NOT NULL",Integer.class));
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM applications",Integer.class));
        } finally { admin.execute("DROP DATABASE "+database+" WITH (FORCE)"); }
    }
}
