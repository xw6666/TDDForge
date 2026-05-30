package com.tddforge.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigPropertiesValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    // --- OpencodeConfig ---

    @Test
    void opencodeConfigBlankConfigPath_reportsViolation() {
        OpencodeConfig config = new OpencodeConfig();
        config.setConfigPath("");
        config.setPlanner(validModelSpec("m"));
        config.setTestWriter(validModelSpec("m"));
        config.setTestReviewer(validModelSpec("m"));
        config.setCoderDefault(validModelSpec("m"));

        Set<ConstraintViolation<OpencodeConfig>> violations = validator.validate(config);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("configPath")
                        && v.getMessage().contains("must not be blank"));
    }

    @Test
    void opencodeConfigNullConfigPath_reportsViolation() {
        OpencodeConfig config = new OpencodeConfig();
        config.setConfigPath(null);
        config.setPlanner(validModelSpec("m"));
        config.setTestWriter(validModelSpec("m"));
        config.setTestReviewer(validModelSpec("m"));
        config.setCoderDefault(validModelSpec("m"));

        Set<ConstraintViolation<OpencodeConfig>> violations = validator.validate(config);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("configPath")
                        && v.getMessage().contains("must not be blank"));
    }

    @Test
    void opencodeConfigNullPlanner_reportsViolation() {
        OpencodeConfig config = new OpencodeConfig();
        config.setConfigPath("/tmp/config.json");
        config.setPlanner(null);
        config.setTestWriter(validModelSpec("m"));
        config.setTestReviewer(validModelSpec("m"));
        config.setCoderDefault(validModelSpec("m"));

        Set<ConstraintViolation<OpencodeConfig>> violations = validator.validate(config);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("planner")
                        && v.getMessage().contains("must be configured"));
    }

    @Test
    void opencodeConfigNullTestWriter_reportsViolation() {
        OpencodeConfig config = new OpencodeConfig();
        config.setConfigPath("/tmp/config.json");
        config.setPlanner(validModelSpec("m"));
        config.setTestWriter(null);
        config.setTestReviewer(validModelSpec("m"));
        config.setCoderDefault(validModelSpec("m"));

        Set<ConstraintViolation<OpencodeConfig>> violations = validator.validate(config);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("testWriter")
                        && v.getMessage().contains("must be configured"));
    }

    @Test
    void opencodeConfigNullTestReviewer_reportsViolation() {
        OpencodeConfig config = new OpencodeConfig();
        config.setConfigPath("/tmp/config.json");
        config.setPlanner(validModelSpec("m"));
        config.setTestWriter(validModelSpec("m"));
        config.setTestReviewer(null);
        config.setCoderDefault(validModelSpec("m"));

        Set<ConstraintViolation<OpencodeConfig>> violations = validator.validate(config);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("testReviewer")
                        && v.getMessage().contains("must be configured"));
    }

    @Test
    void opencodeConfigNullCoderDefault_reportsViolation() {
        OpencodeConfig config = new OpencodeConfig();
        config.setConfigPath("/tmp/config.json");
        config.setPlanner(validModelSpec("m"));
        config.setTestWriter(validModelSpec("m"));
        config.setTestReviewer(validModelSpec("m"));
        config.setCoderDefault(null);

        Set<ConstraintViolation<OpencodeConfig>> violations = validator.validate(config);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("coderDefault")
                        && v.getMessage().contains("must be configured"));
    }

    @Test
    void opencodeConfigBlankModelInNestedSpec_reportsViolation() {
        OpencodeConfig config = new OpencodeConfig();
        config.setConfigPath("/tmp/config.json");
        ModelSpec planner = new ModelSpec();
        planner.setModel("");
        config.setPlanner(planner);
        config.setTestWriter(validModelSpec("m"));
        config.setTestReviewer(validModelSpec("m"));
        config.setCoderDefault(validModelSpec("m"));

        Set<ConstraintViolation<OpencodeConfig>> violations = validator.validate(config);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().contains("planner")
                        && v.getPropertyPath().toString().contains("model")
                        && v.getMessage().contains("must not be blank"));
    }

    @Test
    void opencodeConfigValid_noViolations() {
        OpencodeConfig config = new OpencodeConfig();
        config.setConfigPath("/tmp/config.json");
        config.setPlanner(validModelSpec("planner"));
        config.setTestWriter(validModelSpec("tw"));
        config.setTestReviewer(validModelSpec("tr"));
        config.setCoderDefault(validModelSpec("coder"));

        Set<ConstraintViolation<OpencodeConfig>> violations = validator.validate(config);
        assertThat(violations).isEmpty();
    }

    // --- RepoConfig ---

    @Test
    void repoConfigBlankPath_reportsViolation() {
        RepoConfig config = new RepoConfig();
        config.setPath("");
        config.setWorktreeDir("/tmp/wt");

        Set<ConstraintViolation<RepoConfig>> violations = validator.validate(config);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("path")
                        && v.getMessage().contains("must not be blank"));
    }

    @Test
    void repoConfigBlankBaseBranch_reportsViolation() {
        RepoConfig config = new RepoConfig();
        config.setPath("/repo");
        config.setBaseBranch("");
        config.setWorktreeDir("/tmp/wt");

        Set<ConstraintViolation<RepoConfig>> violations = validator.validate(config);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("baseBranch")
                        && v.getMessage().contains("must not be blank"));
    }

    @Test
    void repoConfigBlankWorktreeDir_reportsViolation() {
        RepoConfig config = new RepoConfig();
        config.setPath("/repo");
        config.setWorktreeDir("");

        Set<ConstraintViolation<RepoConfig>> violations = validator.validate(config);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("worktreeDir")
                        && v.getMessage().contains("must not be blank"));
    }

    @Test
    void repoConfigValid_noViolations() {
        RepoConfig config = new RepoConfig();
        config.setPath("/repo");
        config.setBaseBranch("main");
        config.setWorktreeDir("/tmp/wt");

        Set<ConstraintViolation<RepoConfig>> violations = validator.validate(config);
        assertThat(violations).isEmpty();
    }

    // --- MysqlConfig ---

    @Test
    void mysqlConfigBlankUrl_reportsViolation() {
        MysqlConfig config = new MysqlConfig();
        config.setUrl("");
        config.setUsername("user");
        config.setPassword("pass");

        Set<ConstraintViolation<MysqlConfig>> violations = validator.validate(config);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("url")
                        && v.getMessage().contains("must not be blank"));
    }

    @Test
    void mysqlConfigBlankUsername_reportsViolation() {
        MysqlConfig config = new MysqlConfig();
        config.setUrl("jdbc:mysql://localhost/db");
        config.setUsername("");
        config.setPassword("pass");

        Set<ConstraintViolation<MysqlConfig>> violations = validator.validate(config);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("username")
                        && v.getMessage().contains("must not be blank"));
    }

    @Test
    void mysqlConfigBlankPassword_reportsViolation() {
        MysqlConfig config = new MysqlConfig();
        config.setUrl("jdbc:mysql://localhost/db");
        config.setUsername("user");
        config.setPassword("");

        Set<ConstraintViolation<MysqlConfig>> violations = validator.validate(config);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("password")
                        && v.getMessage().contains("must not be blank"));
    }

    @Test
    void mysqlConfigNullFields_reportsMultipleViolations() {
        MysqlConfig config = new MysqlConfig();

        Set<ConstraintViolation<MysqlConfig>> violations = validator.validate(config);
        assertThat(violations).hasSizeGreaterThanOrEqualTo(3);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("url"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("username"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    // --- config.ModelSpec ---

    @Test
    void configModelSpecBlankModel_reportsViolation() {
        ModelSpec spec = new ModelSpec();
        spec.setModel("");

        Set<ConstraintViolation<ModelSpec>> violations = validator.validate(spec);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("model")
                        && v.getMessage().contains("must not be blank"));
    }

    @Test
    void configModelSpecNullModel_reportsViolation() {
        ModelSpec spec = new ModelSpec();
        spec.setModel(null);

        Set<ConstraintViolation<ModelSpec>> violations = validator.validate(spec);
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("model")
                        && v.getMessage().contains("must not be blank"));
    }

    @Test
    void configModelSpecValid_noViolations() {
        ModelSpec spec = new ModelSpec();
        spec.setModel("gpt-4");

        Set<ConstraintViolation<ModelSpec>> violations = validator.validate(spec);
        assertThat(violations).isEmpty();
    }

    // --- multiple missing fields ---

    @Test
    void opencodeConfigAllNulls_reportsMultipleViolations() {
        OpencodeConfig config = new OpencodeConfig();

        Set<ConstraintViolation<OpencodeConfig>> violations = validator.validate(config);
        assertThat(violations).hasSizeGreaterThanOrEqualTo(5);
    }

    private static ModelSpec validModelSpec(String model) {
        ModelSpec spec = new ModelSpec();
        spec.setModel(model);
        return spec;
    }
}
