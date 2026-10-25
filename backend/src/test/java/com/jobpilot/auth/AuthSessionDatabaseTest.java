package com.jobpilot.auth;

import com.jobpilot.ai.service.CreditService;
import com.jobpilot.auth.controller.AuthController;
import com.jobpilot.auth.dto.*;
import com.jobpilot.auth.filter.JwtAuthenticationFilter;
import com.jobpilot.auth.service.*;
import com.jobpilot.common.config.SecurityConfig;
import com.jobpilot.common.exception.*;
import com.jobpilot.common.util.JwtUtil;
import com.jobpilot.user.entity.User;
import com.jobpilot.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(AuthSessionDatabaseTest.Config.class)
@WebAppConfiguration
@TestPropertySource(properties={"app.jwt.access-token-expiration-ms=900000","app.cors.allowed-origins=https://app.example.test"})
@EnabledIfEnvironmentVariable(named="JOBPILOT_REVIEW_DB_URL",matches=".+")
class AuthSessionDatabaseTest {
    @Configuration @EnableTransactionManagement @EnableWebMvc
    @EnableJpaRepositories(basePackageClasses=UserRepository.class)
    @Import({AuthSessionService.class,AuthService.class,CreditService.class,AuthController.class,
            SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class,Probe.class})
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(System.getenv("JOBPILOT_REVIEW_DB_URL"),"review_test","local_review_test_only"); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean Flyway flyway(DataSource ds) { var f=Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();f.migrate();return f; }
        @Bean @DependsOn("flyway") LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var bean=new LocalContainerEntityManagerFactoryBean();bean.setDataSource(ds);bean.setPackagesToScan("com.jobpilot.user.entity");
            bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            bean.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return bean;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean JwtUtil jwt() { return new JwtUtil(AuthServiceTest.KEY,900000,604800000); }
    }
    @RestController static class Probe { @GetMapping("/api/v1/session-probe") String get() { return "authenticated"; } }
    @Autowired AuthSessionService sessions;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    User user;
    final List<UUID> ownedUsers=new ArrayList<>();
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        user=newUser();
    }
    User newUser() {
        UUID id=UUID.randomUUID();ownedUsers.add(id);
        jdbc.update("INSERT INTO users(id,email,password_hash,full_name) VALUES(?,?,?,?)",id,id+"@auth.test",new BCryptPasswordEncoder(4).encode("test-password"),"Auth Test");
        return users.findById(id).orElseThrow();
    }
    @AfterEach void cleanup() { for(UUID id:ownedUsers)jdbc.update("DELETE FROM users WHERE id=?",id); }
    UUID sid(AuthSessionService.Tokens t) { return jwt.extractSessionId(t.accessToken()); }
    void access(AuthSessionService.Tokens t,int status) throws Exception {
        mvc.perform(get("/api/v1/session-probe").header("Authorization","Bearer "+t.accessToken())).andExpect(status().is(status));
    }
    String refreshBody(String token) { return "{\"refreshToken\":\""+token+"\"}"; }

    @Test void rotationPersistsHashesAndPreservesAbsoluteExpiry() {
        var first=sessions.create(user);UUID sid=sid(first);
        var expiry=jdbc.queryForObject("SELECT expires_at FROM auth_sessions WHERE id=?",java.sql.Timestamp.class,sid);
        var second=sessions.rotate(first.refreshToken());
        assertNotEquals(first.refreshToken(),second.refreshToken());assertEquals(sid,sid(second));
        assertEquals(expiry,jdbc.queryForObject("SELECT expires_at FROM auth_sessions WHERE id=?",java.sql.Timestamp.class,sid));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM auth_refresh_tokens WHERE session_id=?",Integer.class,sid));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM auth_refresh_tokens WHERE session_id=? AND used_at IS NULL",Integer.class,sid));
        for(String hash:jdbc.queryForList("SELECT token_hash FROM auth_refresh_tokens WHERE session_id=?",String.class,sid))assertTrue(hash.matches("[0-9a-f]{64}"));
    }
    @Test void replayReturns401AndCommitsFamilyRevocation() throws Exception {
        var first=sessions.create(user);var second=sessions.rotate(first.refreshToken());access(second,200);
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(first.refreshToken()))).andExpect(status().isUnauthorized());
        access(first,401);access(second,401);
        assertThrows(BadCredentialsException.class,()->sessions.rotate(second.refreshToken()));
        assertEquals("REFRESH_REPLAY",jdbc.queryForObject("SELECT revocation_reason FROM auth_sessions WHERE id=?",String.class,sid(first)));
    }
    @Test void concurrentRefreshHasOneWinnerAndReplayRevokesThatWinner() throws Exception {
        var first=sessions.create(user);var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<AuthSessionService.Tokens> refresh=()->{gate.await();try{return sessions.rotate(first.refreshToken());}catch(BadCredentialsException e){return null;}};
            var a=pool.submit(refresh);var b=pool.submit(refresh);gate.countDown();
            var x=a.get(10,TimeUnit.SECONDS);var y=b.get(10,TimeUnit.SECONDS);
            assertTrue((x==null)!=(y==null));access(x==null?y:x,401);
            assertFalse(sessions.isActive(sid(first),user.getId()));
        }
    }
    @Test void logoutUsingRotatedAncestorRevokesOnlyItsSessionAndIsIdempotent() throws Exception {
        var first=sessions.create(user);var descendant=sessions.rotate(first.refreshToken());var other=sessions.create(user);
        for(int i=0;i<2;i++)mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content(refreshBody(first.refreshToken()))).andExpect(status().isOk());
        access(descendant,401);access(other,200);assertThrows(BadCredentialsException.class,()->sessions.rotate(descendant.refreshToken()));
    }
    @Test void logoutAllIsAuthenticatedAndScopedToTheUser() throws Exception {
        var first=sessions.create(user);var second=sessions.create(user);var stranger=sessions.create(newUser());
        mvc.perform(post("/api/v1/auth/logout-all")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout-all").header("Authorization","Bearer "+first.refreshToken())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout-all").header("Authorization","Bearer "+first.accessToken())).andExpect(status().isOk());
        access(first,401);access(second,401);access(stranger,200);
        assertThrows(BadCredentialsException.class,()->sessions.rotate(second.refreshToken()));
    }
    @Test void disabledThenReenabledAccountCannotRecoverOldSession() throws Exception {
        var t=sessions.create(user);jdbc.update("UPDATE users SET enabled=false WHERE id=?",user.getId());
        assertThrows(BadCredentialsException.class,()->sessions.rotate(t.refreshToken()));access(t,401);
        jdbc.update("UPDATE users SET enabled=true WHERE id=?",user.getId());access(t,401);access(sessions.create(users.findById(user.getId()).orElseThrow()),200);
    }
    @Test void passwordChangeRevokesAllAndRejectsStaleLoginSnapshot() throws Exception {
        var a=sessions.create(user);var b=sessions.create(user);
        jdbc.update("UPDATE users SET password_hash=? WHERE id=?",new BCryptPasswordEncoder(4).encode("changed"),user.getId());
        access(a,401);access(b,401);assertThrows(BadCredentialsException.class,()->sessions.create(user));
        assertThrows(BadCredentialsException.class,()->sessions.rotate(a.refreshToken()));
    }
    @Test void profileEditDoesNotRevokeSession() throws Exception {
        var t=sessions.create(user);jdbc.update("UPDATE users SET full_name='New name' WHERE id=?",user.getId());access(t,200);
    }
    @Test void sessionExpiryIsEnforcedEvenWhenJwtHasNotExpired() throws Exception {
        var t=sessions.create(user);jdbc.update("UPDATE auth_sessions SET expires_at=now()-interval '1 second' WHERE id=?",sid(t));
        assertTrue(jwt.isAccessTokenValid(t.accessToken()));access(t,401);assertThrows(BadCredentialsException.class,()->sessions.rotate(t.refreshToken()));
    }
    @Test void signedButUnissuedRefreshCannotRotateOrRevoke() throws Exception {
        var real=sessions.create(user);
        String unknown=jwt.generateRefreshToken(user.getId(),user.getEmail(),sid(real),new Date(System.currentTimeMillis()+60000));
        assertThrows(BadCredentialsException.class,()->sessions.rotate(unknown));sessions.logout(unknown);access(real,200);
        var stranger=newUser();String wrongOwner=jwt.generateRefreshToken(stranger.getId(),stranger.getEmail(),sid(real),new Date(System.currentTimeMillis()+60000));
        assertThrows(BadCredentialsException.class,()->sessions.rotate(wrongOwner));sessions.logout(wrongOwner);access(real,200);
    }
    @Test void accessTokenCannotRefreshAndUnknownSessionCannotAuthenticate() throws Exception {
        var real=sessions.create(user);assertThrows(BadCredentialsException.class,()->sessions.rotate(real.accessToken()));
        mvc.perform(get("/api/v1/session-probe").header("Authorization","Bearer "+jwt.generateAccessToken(user.getId(),user.getEmail(),"USER"))).andExpect(status().isUnauthorized());
        access(real,200);
    }
    @Test void registerAndLoginCreateIndependentUsableSessions() throws Exception {
        var register=new RegisterRequest();register.setEmail(UUID.randomUUID()+"@auth.test");register.setFullName("Registered User");register.setPassword("test-password");
        var registered=auth.register(register);UUID id=UUID.fromString(registered.getUser().getId());ownedUsers.add(id);
        var login=new LoginRequest();login.setEmail(register.getEmail());login.setPassword(register.getPassword());var loggedIn=auth.login(login);
        assertNotEquals(jwt.extractSessionId(registered.getAccessToken()),jwt.extractSessionId(loggedIn.getAccessToken()));
        assertTrue(sessions.isActive(jwt.extractSessionId(loggedIn.getAccessToken()),id));
        login.setPassword("wrong");assertThrows(BadCredentialsException.class,()->auth.login(login));
        var request=new RefreshRequest();request.setRefreshToken(loggedIn.getRefreshToken());
        assertTrue(jwt.isAccessTokenValid(auth.refresh(request).getAccessToken()));
    }
    @Test void passwordResetTokenIsSingleUseAndRevokesSessions() throws Exception {
        var t=sessions.create(user);String reset=UUID.randomUUID().toString();
        jdbc.update("UPDATE users SET reset_token=?,reset_token_expiry=now()+interval '1 hour' WHERE id=?",reset,user.getId());
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> change=()->{gate.await();try{auth.resetPassword(reset,"replacement-password");return true;}catch(BadRequestException e){return false;}};
            var a=pool.submit(change);var b=pool.submit(change);gate.countDown();assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }
        access(t,401);assertNull(users.findById(user.getId()).orElseThrow().getResetToken());
    }
    @Test void missingResetExpiryIsRejectedWithoutChangingPassword() {
        String reset=UUID.randomUUID().toString();jdbc.update("UPDATE users SET reset_token=?,reset_token_expiry=NULL WHERE id=?",reset,user.getId());
        assertThrows(BadRequestException.class,()->auth.resetPassword(reset,"replacement-password"));
        assertEquals(user.getPasswordHash(),users.findById(user.getId()).orElseThrow().getPasswordHash());
    }
}
