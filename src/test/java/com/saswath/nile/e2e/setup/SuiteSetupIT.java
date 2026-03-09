package com.saswath.nile.e2e.setup;

import com.saswath.nile.e2e.base.AuthHelper;
import com.saswath.nile.e2e.base.BaseIT;
import org.testng.annotations.Test;

/**
 * Setup suite — registers all admin email accounts used across the test classes.
 * This runs as a separate Maven Failsafe phase in CI (mvn verify -Dsuite=setup)
 * BEFORE the MySQL promotion step. Once these users exist in the database,
 * the workflow promotes them to ROLE_ADMIN via a direct SQL UPDATE, then runs
 * the real regression suite.
 * Admin emails must match the pattern used in each IT class @BeforeClass.
 * Since each IT class generates a UUID suffix we cannot predict those emails,
 * so instead we register a fixed set of well-known admin accounts here and
 * the IT classes are updated to use these fixed emails via e2e.properties
 * rather than generating random ones.
 */
public class SuiteSetupIT extends BaseIT {

    @Test(description = "Register the shared admin user account used by all IT classes")
    public void registerAdminUser() {
        String name = prop("test.admin.name");
        String email = prop("test.admin.email");
        String password = prop("test.admin.password");

        // 200 on first run, 400 on re-runs (duplicate) — both are acceptable
        int status = AuthHelper.register(name, email, password)
                .then().extract().statusCode();

        assert status == 200 || status == 400
                : "Unexpected status registering admin: " + status;

        System.out.println("[SuiteSetupIT] Admin user ready: " + email);
    }

    @Test(description = "Register the shared regular user account used by all IT classes")
    public void registerTestUser() {
        String name = prop("test.user.name");
        String email = prop("test.user.email");
        String password = prop("test.user.password");

        int status = AuthHelper.register(name, email, password)
                .then().extract().statusCode();

        assert status == 200 || status == 400
                : "Unexpected status registering user: " + status;

        System.out.println("[SuiteSetupIT] Test user ready: " + email);
    }
}