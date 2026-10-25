package com.jobpilot.ai.service;

import com.jobpilot.auth.dto.RegisterRequest;
import com.jobpilot.auth.filter.JwtAuthenticationFilter.AuthenticatedUser;
import com.jobpilot.auth.service.AuthService;
import com.jobpilot.common.util.JwtUtil;
import com.jobpilot.user.repository.UserRepository;
import com.jobpilot.user.service.UserService;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(CreditAccountJpaTest.Config.class)
@TestPropertySource(properties="app.jwt.access-token-expiration-ms=900000")
@EnabledIfEnvironmentVariable(named="JOBPILOT_REVIEW_DB_URL",matches=".+")
class CreditAccountJpaTest {
    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses=UserRepository.class)
    @Import({CreditService.class,AuthService.class,UserService.class,com.jobpilot.auth.service.AuthSessionService.class})
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(System.getenv("JOBPILOT_REVIEW_DB_URL"),"review_test","local_review_test_only"); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean Flyway flyway(DataSource ds) { var f=Flyway.configure().dataSource(ds).locations("classpath:db/migration").load(); f.migrate(); return f; }
        @Bean @DependsOn("flyway") LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var bean=new LocalContainerEntityManagerFactoryBean(); bean.setDataSource(ds); bean.setPackagesToScan("com.jobpilot.user.entity");
            bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            bean.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return bean;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean JwtUtil jwt() { return new JwtUtil("test-only-signing-key-at-least-32-bytes-long",900000,604800000); }
        @Bean PasswordEncoder encoder() { var encoder=mock(PasswordEncoder.class); when(encoder.encode(anyString())).thenReturn("test-hash"); return encoder; }
    }
    @Autowired AuthService auth;
    @Autowired UserService profiles;
    @Autowired UserRepository users;
    @Autowired CreditService credits;
    @Autowired JdbcTemplate jdbc;
    @Autowired JpaTransactionManager manager;
    UUID id;
    @BeforeEach void register() {
        var request=new RegisterRequest(); request.setEmail(UUID.randomUUID()+"@jpa-credit.test");request.setPassword("test-password");request.setFullName("Test User");
        var response=auth.register(request);id=UUID.fromString(response.getUser().getId());
        assertEquals(5,response.getUser().getCredits());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(id,request.getEmail(),"USER"),null));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext();if(id!=null)jdbc.update("DELETE FROM users WHERE id=?",id); }
    @Test void staleJpaEntityCannotRestoreBalanceAndProfileReportsSpendableBalance() {
        new TransactionTemplate(manager).executeWithoutResult(status->{
            var stale=users.findById(id).orElseThrow();
            var ticket=credits.reserve("one","inputs","test","model");
            credits.settle(ticket,"model",null,null,null,1);
            stale.setFullName("Edited");users.saveAndFlush(stale);
        });
        assertEquals(4,profiles.getCurrentUser().getCredits());assertEquals(4,credits.getRemainingCredits());
    }
    @Test void independentLedgerTransactionSurvivesJpaRollback() {
        new TransactionTemplate(manager).executeWithoutResult(status->{
            var ticket=credits.reserve("one","inputs","test","model");credits.settle(ticket,"model",null,null,null,1);
            status.setRollbackOnly();
        });
        assertEquals(4,credits.getRemainingCredits());
    }
}
