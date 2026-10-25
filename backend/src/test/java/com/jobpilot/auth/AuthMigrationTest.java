package com.jobpilot.auth;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="JOBPILOT_REVIEW_DB_URL",matches=".+")
class AuthMigrationTest {
    @Test void upgradeRevokesLegacyTokensAndPreservesAccountsAndPasswordHashes() {
        String root=System.getenv("JOBPILOT_REVIEW_DB_URL");
        var admin=new JdbcTemplate(new DriverManagerDataSource(root,"review_test","local_review_test_only"));
        String database="auth_upgrade_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE DATABASE "+database);
        try {
            var ds=new DriverManagerDataSource(root.replaceFirst("/[^/?]+(\\?.*)?$","/"+database),"review_test","local_review_test_only");
            Flyway.configure().dataSource(ds).target("20").locations("classpath:db/migration").load().migrate();
            var jdbc=new JdbcTemplate(ds);UUID user=UUID.randomUUID();
            jdbc.update("INSERT INTO users(id,email,password_hash,full_name) VALUES(?,?,?,?)",user,"upgrade@auth.test","preserved-test-hash","Preserved User");
            jdbc.update("INSERT INTO refresh_tokens(user_id,token,expires_at) VALUES(?,?,now()+interval '7 days')",user,"legacy-test-token");
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
            assertEquals("preserved-test-hash",jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,user));
            assertEquals("Preserved User",jdbc.queryForObject("SELECT full_name FROM users WHERE id=?",String.class,user));
            assertEquals(true,jdbc.queryForObject("SELECT revoked FROM refresh_tokens WHERE user_id=?",Boolean.class,user));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM auth_sessions",Integer.class));
            UUID session=UUID.randomUUID();
            jdbc.update("INSERT INTO auth_sessions(id,user_id,expires_at) VALUES(?,?,now()+interval '7 days')",session,user);
            jdbc.update("UPDATE users SET enabled=false WHERE id=?",user);
            assertEquals("ACCOUNT_CHANGED",jdbc.queryForObject("SELECT revocation_reason FROM auth_sessions WHERE id=?",String.class,session));
        } finally { admin.execute("DROP DATABASE "+database+" WITH (FORCE)"); }
    }
}
