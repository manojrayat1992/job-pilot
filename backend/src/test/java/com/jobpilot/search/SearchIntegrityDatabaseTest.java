package com.jobpilot.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.controller.SearchIndexController;
import com.jobpilot.ai.dto.*;
import com.jobpilot.ai.service.*;
import com.jobpilot.application.dto.CreateApplicationRequest;
import com.jobpilot.application.repository.ApplicationRepository;
import com.jobpilot.application.service.ApplicationService;
import com.jobpilot.auth.filter.JwtAuthenticationFilter.AuthenticatedUser;
import com.jobpilot.common.exception.*;
import com.jobpilot.job.entity.Job;
import com.jobpilot.job.repository.JobRepository;
import com.jobpilot.job.service.*;
import com.jobpilot.resume.repository.ResumeRepository;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;
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
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(SearchIntegrityDatabaseTest.Config.class)
@EnabledIfEnvironmentVariable(named="JOBPILOT_REVIEW_DB_URL",matches=".+")
class SearchIntegrityDatabaseTest {
    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses={JobRepository.class,ApplicationRepository.class,ResumeRepository.class})
    @Import({EmbeddingService.class,EmbeddingWorker.class,JobWriter.class,JobService.class,ApplicationService.class,AgentService.class,SearchIndexController.class})
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource(System.getenv("JOBPILOT_REVIEW_DB_URL"),"review_test","local_review_test_only");}
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean Flyway flyway(DataSource ds){var f=Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();f.migrate();return f;}
        @Bean @DependsOn("flyway") LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){
            var b=new LocalContainerEntityManagerFactoryBean();b.setDataSource(ds);
            b.setPackagesToScan("com.jobpilot.job.entity","com.jobpilot.application.entity","com.jobpilot.resume.entity");
            b.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            b.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return b;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory){return new JpaTransactionManager(factory);}
        @Bean ObjectMapper mapper(){return new ObjectMapper();}
        @Bean EmbeddingModel model(){return mock(EmbeddingModel.class);}
        @Bean AiService ai(){return mock(AiService.class);}
        @Bean ChatGenerationService chat(){return mock(ChatGenerationService.class);}
        @Bean SerpApiClient search(){return mock(SerpApiClient.class);}
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource ds;
    @Autowired JpaTransactionManager manager;
    @Autowired EmbeddingService embeddings;
    @Autowired EmbeddingWorker worker;
    @Autowired EmbeddingModel model;
    @Autowired JobWriter writer;
    @Autowired JobRepository jobs;
    @Autowired JobService jobService;
    @Autowired ApplicationService applications;
    @Autowired AgentService agent;
    @Autowired SearchIndexController indexing;
    @Autowired ChatGenerationService chat;
    @Autowired SerpApiClient search;
    @Autowired AiService ai;
    final UUID user=UUID.randomUUID(),foreign=UUID.randomUUID();
    @BeforeEach void setup(){
        for(UUID id:List.of(user,foreign))jdbc.update("INSERT INTO users(id,email,password_hash,full_name) VALUES(?,?,?,?)",id,id+"@search.test","test-only","Test User");
        authenticate(user);reset(model,chat,search,ai);
        when(model.embed(anyString())).thenReturn(vector(0));
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();jdbc.update("DELETE FROM users WHERE id IN (?,?)",user,foreign);}
    void authenticate(UUID id){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(id,"search@test.example","USER"),null));}
    static float[] vector(int axis){var v=new float[384];v[axis]=1;return v;}
    Job job(UUID owner,String url,String text){return Job.builder().userId(owner).title("Engineer").company("Example").location("Remote").sourceUrl(url).rawText(text).build();}
    UUID save(String url,String text){return writer.save(job(user,url,text));}
    UUID resume(String text){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO resumes(id,user_id,title,raw_text) VALUES(?,?,?,?)",id,user,"Source",text);return id;}
    String status(UUID id){return jdbc.queryForObject("SELECT status FROM embedding_tasks WHERE entity_id=?",String.class,id);}
    String stored(UUID id){return jdbc.queryForObject("SELECT embedding::text FROM job_embeddings WHERE job_id=?",String.class,id);}
    int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE user_id=?",Integer.class,user);}

    @Test void sourceAndQueueCommitTogetherAndRollbackLeavesNeither(){
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            UUID id=save("https://example.test/rollback","body");
            assertEquals("PENDING",status(id));
            try(var connection=ds.getConnection();var statement=connection.prepareStatement("SELECT count(*) FROM embedding_tasks WHERE entity_id=?")){
                statement.setObject(1,id);try(var result=statement.executeQuery()){result.next();assertEquals(0,result.getInt(1));}
            }catch(Exception e){throw new AssertionError(e);}
            tx.setRollbackOnly();
        });
        assertEquals(0,count("jobs"));assertNull(worker.claim());
    }
    @Test void canonicalStoreIsSearchableWithOwnerIsolationAndPendingRowsExcluded(){
        UUID relevant=save("https://example.test/relevant","matching");UUID other=save("https://example.test/other","unrelated");
        writer.save(job(foreign,"https://example.test/foreign","matching"));
        when(model.embed("unrelated")).thenReturn(vector(1));worker.poll();
        save("https://example.test/pending","matching");
        var results=jobs.findTop5ByUserIdOrderByEmbedding(user,vector(0));
        assertEquals(List.of(relevant,other),results.stream().map(Job::getId).toList());
        assertNotNull(stored(relevant));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM jobs WHERE user_id=? AND embedding IS NOT NULL",Integer.class,user));
    }
    @Test void recreatedWorkerRecoversExpiredLeaseAndRejectsLateOldResult(){
        UUID id=save("https://example.test/restart","body");var old=worker.claim();
        jdbc.update("UPDATE embedding_tasks SET lease_until=now()-interval '1 second' WHERE entity_id=?",id);
        var fresh=new EmbeddingWorker(jdbc,embeddings,manager);var next=fresh.claim();assertNotEquals(old.token(),next.token());
        fresh.process(next);String saved=stored(id);
        when(model.embed(anyString())).thenReturn(vector(1));worker.process(old);
        assertEquals(saved,stored(id));assertEquals("COMPLETED",status(id));
    }
    @Test void sourceEditDuringInferenceFencesOldWorkerAndQueuesLatestText(){
        UUID id=save("https://example.test/change","old");var task=worker.claim();
        when(model.embed("old")).thenAnswer(call->{jdbc.update("UPDATE jobs SET raw_text='new' WHERE id=?",id);return vector(0);});
        worker.process(task);assertEquals("PENDING",status(id));assertTrue(jdbc.queryForList("SELECT * FROM job_embeddings WHERE job_id=?",id).isEmpty());
        when(model.embed("new")).thenReturn(vector(1));worker.process(worker.claim());
        assertTrue(jdbc.queryForObject("SELECT embedding=cast(? as vector) FROM job_embeddings WHERE job_id=?",Boolean.class,EmbeddingService.vectorLiteral(vector(1)),id));
    }
    @Test void sourceDeletionDuringInferenceCannotLeaveOrphanWorkOrVectors(){
        UUID id=save("https://example.test/delete","delete me");var task=worker.claim();
        when(model.embed("delete me")).thenAnswer(call->{jdbc.update("DELETE FROM jobs WHERE id=?",id);return vector(0);});
        worker.process(task);assertTrue(jdbc.queryForList("SELECT * FROM embedding_tasks WHERE entity_id=?",id).isEmpty());
        assertTrue(jdbc.queryForList("SELECT * FROM job_embeddings WHERE job_id=?",id).isEmpty());
    }
    @Test void failedResumeGenerationRetainsOldVectorsButHidesStaleContextUntilRetry(){
        UUID id=resume("Original resume text");worker.poll();
        jdbc.update("UPDATE resumes SET raw_text=? WHERE id=?","A".repeat(1800),id);
        doReturn(vector(0)).doThrow(new IllegalStateException("local model unavailable")).when(model).embed(anyString());
        worker.process(worker.claim());assertEquals("FAILED",status(id));
        assertEquals("Original resume text",jdbc.queryForObject("SELECT chunk_text FROM resume_embeddings WHERE resume_id=?",String.class,id));
        doReturn(vector(0)).when(model).embed(anyString());assertNull(embeddings.buildRagContext(user,"query"));
        indexing.retry("RESUME",id);worker.poll();assertEquals("COMPLETED",status(id));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM resume_embeddings WHERE resume_id=?",Integer.class,id));
        assertNotNull(embeddings.buildRagContext(user,"query"));assertNull(embeddings.buildRagContext(foreign,"query"));
    }
    @Test void databaseFailureRollsBackWholeVectorReplacement(){
        UUID id=save("https://example.test/storage","old");worker.poll();String previous=stored(id);
        jdbc.update("UPDATE jobs SET raw_text='replacement' WHERE id=?",id);
        var failing=spy(jdbc);doThrow(new IllegalStateException("storage unavailable")).when(failing).update(startsWith("INSERT INTO job_embeddings"),any(Object[].class));
        var broken=new EmbeddingWorker(failing,embeddings,manager);broken.process(broken.claim());
        assertEquals("FAILED",status(id));assertEquals(previous,stored(id));
        assertTrue(jobs.findTop5ByUserIdOrderByEmbedding(user,vector(0)).isEmpty());
    }
    @Test void retriesBackOffStopAtFiveAndCanBeExplicitlyRequeued(){
        UUID id=save("https://example.test/retry","body");doThrow(new IllegalStateException("model down")).when(model).embed(anyString());
        for(int i=0;i<5;i++){
            var task=worker.claim();assertNotNull(task);worker.process(task);
            assertNull(worker.claim());jdbc.update("UPDATE embedding_tasks SET available_at=now()-interval '1 second' WHERE entity_id=?",id);
        }
        assertNull(worker.claim());assertEquals(5,jdbc.queryForObject("SELECT attempts FROM embedding_tasks WHERE entity_id=?",Integer.class,id));
        indexing.retry("JOB",id);assertNotNull(worker.claim());
    }
    @Test void invalidDimensionsNonFiniteAndZeroVectorsNeverReachStorage(){
        var nan=vector(0);nan[2]=Float.NaN;var infinity=vector(0);infinity[3]=Float.POSITIVE_INFINITY;
        for(var invalid:List.of(new float[3],new float[384],nan,infinity)){
            UUID id=save("https://example.test/"+UUID.randomUUID(),"body");doReturn(invalid).when(model).embed(anyString());
            worker.process(worker.claim());assertEquals("FAILED",status(id));
            assertTrue(jdbc.queryForList("SELECT * FROM job_embeddings WHERE job_id=?",id).isEmpty());
        }
    }
    @Test void chunkingDoesNotAddAnExtraTrailingOverlapChunk(){assertEquals(1,embeddings.resumeChunks("x".repeat(1000)).size());assertEquals(2,embeddings.resumeChunks("x".repeat(1800)).size());}
    @Test void blankSourcesCompleteWithoutBogusZeroVectors(){UUID id=save("https://example.test/empty","");worker.poll();assertEquals("COMPLETED",status(id));assertTrue(jobs.findTop5ByUserIdOrderByEmbedding(user,vector(0)).isEmpty());verify(model,never()).embed(anyString());}
    @Test void foreignUsersCannotReadOrRetryIndexingAndCannotAttachForeignJob(){
        UUID id=save("https://example.test/owner","body");authenticate(foreign);
        assertTrue(indexing.list().getData().isEmpty());assertThrows(ResourceNotFoundException.class,()->indexing.retry("JOB",id));
        var request=new AgentAutoApplyRequest();request.setJobs(List.of(AgentResearchResponse.RankedJob.builder().id(id).title("Fake").company("Fake").source("Internal").build()));
        assertThrows(ResourceNotFoundException.class,()->agent.saveJobs(request));
    }
    @Test void simultaneousIdenticalUrlSavesReturnOneRecordAndOneQueueTask()throws Exception{
        var barrier=new CyclicBarrier(8);var ids=new HashSet<UUID>();
        try(var executor=Executors.newFixedThreadPool(8)){
            var futures=new ArrayList<Future<UUID>>();
            for(int i=0;i<8;i++)futures.add(executor.submit(()->{barrier.await(10,TimeUnit.SECONDS);return save(" https://example.test/shared ",UUID.randomUUID().toString());}));
            for(var f:futures)ids.add(f.get(15,TimeUnit.SECONDS));
        }
        assertEquals(1,ids.size());assertEquals(1,count("jobs"));
        assertEquals(1L,jdbc.queryForObject("SELECT generation FROM embedding_tasks WHERE entity_id=?",Long.class,ids.iterator().next()));
    }
    @Test void sameSourceAcrossUsersIsIndependentAndBlankLinksUseContentIdentity(){
        UUID first=save("https://example.test/shared","first");assertNotEquals(first,writer.save(job(foreign,"https://example.test/shared","first")));
        UUID blank=save("#","body");assertEquals(blank,save("","body"));assertEquals(blank,save(null,"body"));
        assertNotEquals(blank,save(null,"different body"));
        assertEquals("first",jobs.findByIdAndUserId(save("https://example.test/shared","changed"),user).orElseThrow().getRawText());
    }
    @Test void directDatabaseInsertsCannotBypassDuplicateGuard(){
        save("https://example.test/unique","body");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO jobs(user_id,source_url,raw_text) VALUES(?,?,?)",user,"https://example.test/unique","second"));
    }
    @Test void analyzeExistingUrlReusesSavedJobBeforeChargingForParsing(){
        UUID id=save("https://example.test/existing","body");var request=new com.jobpilot.job.dto.JobAnalysisRequest();request.setUrl(" https://example.test/existing ");
        assertEquals(id.toString(),jobService.analyzeJob(request).getId());verifyNoInteractions(ai);
    }
    @Test void normalAnalysisPersistsParsedFieldsAndQueuesSearchableEmbedding(){
        when(ai.parseJobDescription("Java backend role")).thenReturn(Map.of("title","Backend engineer","company","Example",
                "requiredSkills",List.of("Java","SQL"),"niceToHaveSkills",List.of("Kotlin"),"jobType","FULL_TIME"));
        var request=new com.jobpilot.job.dto.JobAnalysisRequest();request.setText("Java backend role");request.setUrl("https://example.test/analyzed");
        var result=jobService.analyzeJob(request);UUID id=UUID.fromString(result.getId());
        assertEquals("Backend engineer",result.getTitle());assertArrayEquals(new String[]{"Java","SQL"},result.getSkillsRequired());
        assertEquals("FULL_TIME",result.getJobType());assertEquals("PENDING",status(id));
        worker.poll();assertEquals(id,jobs.findTop5ByUserIdOrderByEmbedding(user,vector(0)).getFirst().getId());
    }
    CreateApplicationRequest application(UUID job){var r=new CreateApplicationRequest();r.setCompany("Example");r.setPosition("Engineer");r.setJobId(job);r.setStatus("APPLIED");r.setNotes("User's notes");return r;}
    AgentAutoApplyRequest selected(UUID job){var r=new AgentAutoApplyRequest();r.setJobs(List.of(AgentResearchResponse.RankedJob.builder().id(job).title("Engineer").company("Example").source("Internal").build()));return r;}
    @Test void repeatedSelectedSavePreservesExistingApplicationStatusAndNotes(){
        UUID id=save("https://example.test/app","body");var first=applications.create(application(id));agent.saveJobs(selected(id));
        assertEquals(1,count("applications"));var saved=applications.get(UUID.fromString(first.getId()));assertEquals("APPLIED",saved.getStatus());assertEquals("User's notes",saved.getNotes());
    }
    @Test void manualAndResearchSavesRaceToOneTrackerEntry()throws Exception{
        UUID id=save("https://example.test/race","body");var barrier=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)){
            var manual=executor.submit(()->{authenticate(user);try{barrier.await(10,TimeUnit.SECONDS);applications.create(application(id));return true;}finally{SecurityContextHolder.clearContext();}});
            var research=executor.submit(()->{authenticate(user);try{barrier.await(10,TimeUnit.SECONDS);agent.saveJobs(selected(id));return true;}finally{SecurityContextHolder.clearContext();}});
            assertTrue(manual.get(15,TimeUnit.SECONDS));assertTrue(research.get(15,TimeUnit.SECONDS));
        }assertEquals(1,count("applications"));
    }
    @Test void researchUsesSourceFieldsAndIdsInsteadOfGeneratedListingFields(){
        when(search.searchJobs(anyString())).thenReturn(List.of(Map.of("title","Real role","company","Real company","url","https://real.example/job","description","Original description","location","Remote")));
        when(chat.generate(any(),anyString(),eq(2000),eq(.7))).thenReturn(new ChatGenerationService.Result("{\"report\":\"Match\",\"jobs\":[{\"candidateId\":\"candidate-0\",\"matchScore\":80,\"title\":\"Fake\",\"url\":\"https://fake.example\"}]}","model"));
        var request=new AgentResearchRequest();request.setQuery("Engineer");var result=agent.researchJobs(request);
        var found=result.getJobs().getFirst();assertNotNull(found.getId());assertEquals("Real role",found.getTitle());assertEquals("https://real.example/job",found.getUrl());
        assertEquals("Original description",jobs.findByIdAndUserId(found.getId(),user).orElseThrow().getRawText());assertEquals("PENDING",status(found.getId()));
    }
    @Test void unknownModelCandidateCannotCreateInventedJobs(){
        when(search.searchJobs(anyString())).thenReturn(List.of(Map.of("title","Real role","company","Real company","url","https://real.example/job","description","Text","location","Remote")));
        when(chat.generate(any(),anyString(),eq(2000),eq(.7))).thenReturn(new ChatGenerationService.Result("{\"report\":\"Match\",\"jobs\":[{\"candidateId\":\"invented\"}]}","model"));
        var request=new AgentResearchRequest();request.setQuery("Engineer");assertThrows(AiServiceException.class,()->agent.researchJobs(request));assertEquals(0,count("jobs"));
    }
}
