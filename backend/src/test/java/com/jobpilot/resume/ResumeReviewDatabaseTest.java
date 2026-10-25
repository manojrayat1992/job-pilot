package com.jobpilot.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.service.AiService;
import com.jobpilot.auth.filter.JwtAuthenticationFilter.AuthenticatedUser;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.job.repository.JobRepository;
import com.jobpilot.resume.dto.*;
import com.jobpilot.resume.repository.*;
import com.jobpilot.resume.service.*;
import com.jobpilot.storage.service.FileStorageService;
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
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in integration test. Use an empty disposable database, never a development/customer database. */
@SpringJUnitConfig(ResumeReviewDatabaseTest.Config.class)
@EnabledIfEnvironmentVariable(named = "JOBPILOT_REVIEW_DB_URL", matches = ".+")
class ResumeReviewDatabaseTest {
    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ResumeRepository.class)
    @Import({ResumeService.class, ResumeDraftService.class, ResumeContentService.class, PdfExportService.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("JOBPILOT_REVIEW_DB_URL"), "review_test", "local_review_test_only");
        }
        @Bean Flyway flyway(DataSource ds) {
            Flyway flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
            flyway.migrate(); return flyway;
        }
        @Bean @DependsOn("flyway") LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var bean = new LocalContainerEntityManagerFactoryBean();
            bean.setDataSource(ds); bean.setPackagesToScan("com.jobpilot.resume.entity");
            bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            bean.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return bean;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean AiService ai() { return mock(AiService.class); }
        @Bean ResumeParserService parser() { return mock(ResumeParserService.class); }
        @Bean FileStorageService storage() { return mock(FileStorageService.class); }
        @Bean JobRepository jobs() { return mock(JobRepository.class); }
    }
    @Autowired ResumeService resumes;
    @Autowired ResumeDraftService drafts;
    @Autowired ResumeRepository repository;
    @Autowired AiService ai;
    @Autowired DataSource dataSource;
    private UUID userId;
    private JdbcTemplate jdbc;
    private ResumeDTO source;

    private void authenticate(UUID id) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(id, "review@example.test", "USER"), null));
    }
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(dataSource);
        userId = UUID.randomUUID();
        jdbc.update("insert into users(id,email,password_hash,full_name) values(?,?,?,?)", userId, userId + "@example.test", "test-only", "Review Test");
        authenticate(userId);
        reset(ai);
        when(ai.tailorResume(anyMap(), anyString())).thenAnswer(call -> {
            var copy = new ObjectMapper().convertValue(call.getArgument(0), Map.class);
            copy.put("summary", "Engineer focused on reliable services."); return copy;
        });
        source = resumes.createResume("Original", ResumeReviewTest.sample());
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        jdbc.update("delete from users where id=?", userId);
    }
    private ResumeDraftDTO generate() { return drafts.generate(UUID.fromString(source.getId()), new TailorResumeRequest(null, "Java services", null)); }

    @Test void fullMigrationAndApprovalKeepSourceAndExportStable() {
        String original = jdbc.queryForObject("select parsed_json::text from resumes where id=?", String.class, UUID.fromString(source.getId()));
        var draft = generate();
        var approved = drafts.approve(draft.id(), true);
        assertNotNull(approved.approvedResumeId());
        assertEquals(1, jdbc.queryForObject("select count(*) from flyway_schema_history where success and version = '16'", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from flyway_schema_history where success and version = '17'", Integer.class));
        assertEquals("llama-3.1-8b-instant", jdbc.queryForObject("select model_path from model_versions where version_name = 'base-llama-3.1'", String.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from model_versions where version_name = 'gpt-4o-mini-default' and is_active", Integer.class));
        assertEquals(original, jdbc.queryForObject("select parsed_json::text from resumes where id=?", String.class, UUID.fromString(source.getId())));
        var saved = resumes.getResume(approved.approvedResumeId());
        assertEquals(source.getId(), saved.getParentResumeId());
        assertTrue(saved.getRawText().contains("focused on reliable services"));
        assertTrue(drafts.download(draft.id()).length > 500);
        var edited = ResumeReviewTest.sample(); edited.put("summary", "Later manual edit.");
        var newer = resumes.updateResume(approved.approvedResumeId(), Map.of("parsedJson", edited));
        assertNotEquals(saved.getId(), newer.getId());
        assertEquals(saved.getParsedJson(), resumes.getResume(approved.approvedResumeId()).getParsedJson());
    }

    @Test void concurrentApprovalsCreateExactlyOneResume() throws Exception {
        var draft = generate();
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<UUID> approve = () -> {
            authenticate(userId);
            try { start.await(); return drafts.approve(draft.id(), true).approvedResumeId(); }
            finally { SecurityContextHolder.clearContext(); }
        };
        try {
            var first = executor.submit(approve); var second = executor.submit(approve); start.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("select count(*) from resumes where parent_resume_id=?", Integer.class, UUID.fromString(source.getId())));
        } finally { executor.shutdownNow(); }
    }

    @Test void foreignUserCannotReadOrApproveStoredDraft() {
        var draft = generate(); authenticate(UUID.randomUUID());
        assertThrows(ResourceNotFoundException.class, () -> drafts.get(draft.id()));
        assertThrows(ResourceNotFoundException.class, () -> drafts.approve(draft.id(), true));
        assertThrows(ResourceNotFoundException.class, () -> drafts.download(draft.id()));
    }

    @Test void deletingApprovedCopyDoesNotResurrectItOnApprovalRetry() {
        var draft = generate(); var approved = drafts.approve(draft.id(), true);
        resumes.deleteResume(approved.approvedResumeId());
        assertNull(drafts.get(draft.id()).approvedResumeId());
        assertThrows(com.jobpilot.common.exception.BadRequestException.class, () -> drafts.approve(draft.id(), true));
    }

    @Test void entityUpdatesCannotOverwriteContentColumns() {
        var existing = repository.findById(UUID.fromString(source.getId())).orElseThrow();
        existing.setRawText("Overwritten"); existing.setParsedJson(Map.of("summary", "Overwritten"));
        repository.saveAndFlush(existing);
        var reloaded = resumes.getResume(UUID.fromString(source.getId()));
        assertEquals(source.getRawText(), reloaded.getRawText());
        assertEquals(source.getParsedJson(), reloaded.getParsedJson());
    }
}
