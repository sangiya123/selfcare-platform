package com.selfcare.reporting.catalog;

import org.springframework.stereotype.Component;
import java.util.List;

/** Canonical in-code catalog of all governed report definitions. Immutable. */
@Component
public class ReportCatalog {

    public List<ReportDefinition> getAllReports() {
        return List.of(
            ReportDefinition.builder().id("daily-active-users").name("Daily Active Users (DAU)")
                .description("Count of unique users with at least one authenticated action per day.")
                .category(ReportCategory.CUSTOMER_ADOPTION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT date(login_time) AS rdate, tenant_id, COUNT(DISTINCT user_id) AS dau FROM user_sessions WHERE tenant_id = :tenantId AND login_time >= :fromDate AND login_time < :toDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build(),
            ParameterSpec.builder().name("toDate").label("To Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("rdate").label("Date").type(ColumnSpec.ColumnType.DATE).semanticLabel("date").build(),
            ColumnSpec.builder().key("dau").label("DAU").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("dau").build()))
                .defaultSchedule("0 0 1 * * ?").owner("analytics-team@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("user_sessions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("weekly-active-users").name("Weekly Active Users (WAU)")
                .description("Rolling 7-day distinct active users.")
                .category(ReportCategory.CUSTOMER_ADOPTION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT year(login_time)*100+week(login_time) AS week_id, tenant_id, COUNT(DISTINCT user_id) AS wau FROM user_sessions WHERE tenant_id = :tenantId AND login_time >= :fromDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("week_id").label("Week").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("week").build(),
            ColumnSpec.builder().key("wau").label("WAU").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("wau").build()))
                .defaultSchedule("0 0 1 * * ?").owner("analytics-team@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("user_sessions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("monthly-active-users").name("Monthly Active Users (MAU)")
                .description("Monthly distinct active users. Primary engagement KPI.")
                .category(ReportCategory.CUSTOMER_ADOPTION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT year(login_time)*100+month(login_time) AS month_id, tenant_id, COUNT(DISTINCT user_id) AS mau FROM user_sessions WHERE tenant_id = :tenantId AND login_time >= :fromDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("month_id").label("Month").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("month").build(),
            ColumnSpec.builder().key("mau").label("MAU").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("mau").build()))
                .defaultSchedule("0 0 1 * * ?").owner("analytics-team@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("user_sessions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("new-vs-returning-users").name("New vs Returning Users")
                .description("Splits daily users by first-seen date.")
                .category(ReportCategory.CUSTOMER_ADOPTION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT report_date, tenant_id, SUM(is_new) AS new_users, SUM(1-is_new) AS returning_users FROM (SELECT date(s.login_time) AS report_date, s.tenant_id, s.user_id, CASE WHEN f.login_date = date(s.login_time) THEN 1 ELSE 0 END AS is_new FROM user_sessions s JOIN (SELECT user_id, MIN(date(login_time)) AS login_date FROM user_sessions GROUP BY user_id) f USING(user_id)) x WHERE tenant_id = :tenantId GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("new_users").label("New Users").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("new_users").build(),
            ColumnSpec.builder().key("returning_users").label("Returning Users").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("returning_users").build()))
                .defaultSchedule("0 0 1 * * ?").owner("analytics-team@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("user_sessions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("platform-breakdown").name("Platform Breakdown")
                .description("DAU by device platform (iOS/Android/Huawei/Web).")
                .category(ReportCategory.CUSTOMER_ADOPTION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT date(s.login_time) AS rdate, u.platform, COUNT(DISTINCT s.user_id) AS users FROM user_sessions s JOIN users u ON u.id = s.user_id WHERE s.tenant_id = :tenantId AND s.login_time >= :fromDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("platform").label("Platform").type(ColumnSpec.ColumnType.STRING).semanticLabel("platform").build(),
            ColumnSpec.builder().key("users").label("Users").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("users").build()))
                .defaultSchedule("0 0 1 * * ?").owner("analytics-team@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("user_sessions", "users"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("language-distribution").name("Language Distribution")
                .description("Active users by language preference.")
                .category(ReportCategory.CUSTOMER_ADOPTION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT u.preferred_language AS lang_code, COUNT(DISTINCT s.user_id) AS users FROM user_sessions s JOIN users u ON u.id = s.user_id WHERE s.tenant_id = :tenantId AND s.login_time >= :fromDate GROUP BY 1 ORDER BY 2 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("lang_code").label("Language").type(ColumnSpec.ColumnType.STRING).semanticLabel("language_code").build(),
            ColumnSpec.builder().key("users").label("Users").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("users").build()))
                .defaultSchedule("0 0 1 * * ?").owner("analytics-team@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("user_sessions", "users"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("session-duration-stats").name("Session Duration Stats")
                .description("Average session duration per day.")
                .category(ReportCategory.CUSTOMER_ADOPTION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT date(start_time) AS rdate, AVG(duration_sec) AS avg_sec, COUNT(*) AS session_count FROM sessions WHERE tenant_id = :tenantId AND start_time >= :fromDate GROUP BY 1 ORDER BY 1")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("avg_sec").label("Avg Duration (sec)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("session_duration_sec").build(),
            ColumnSpec.builder().key("session_count").label("Sessions").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("session_count").build()))
                .defaultSchedule("0 0 1 * * ?").owner("analytics-team@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("sessions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("app-version-distribution").name("App Version Distribution")
                .description("DAU by app version.")
                .category(ReportCategory.CUSTOMER_ADOPTION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT u.app_version, COUNT(DISTINCT s.user_id) AS users FROM user_sessions s JOIN users u ON u.id = s.user_id WHERE s.tenant_id = :tenantId AND s.login_time >= :fromDate GROUP BY 1 ORDER BY 2 DESC LIMIT 50")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("app_version").label("App Version").type(ColumnSpec.ColumnType.STRING).semanticLabel("app_version").build(),
            ColumnSpec.builder().key("users").label("Users").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("users").build()))
                .defaultSchedule("0 0 1 * * ?").owner("analytics-team@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("user_sessions", "users"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("login-method-breakdown").name("Login Method Breakdown")
                .description("Authentication method usage.")
                .category(ReportCategory.CUSTOMER_ADOPTION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT login_method, COUNT(*) AS login_count FROM user_sessions WHERE tenant_id = :tenantId AND login_time >= :fromDate GROUP BY 1 ORDER BY 2 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("login_method").label("Login Method").type(ColumnSpec.ColumnType.STRING).semanticLabel("login_method").build(),
            ColumnSpec.builder().key("login_count").label("Login Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("login_count").build()))
                .defaultSchedule("0 0 1 * * ?").owner("analytics-team@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("user_sessions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("data-usage-by-day").name("Data Usage by Day")
                .description("Daily data consumption per connection.")
                .category(ReportCategory.USAGE_BILLING).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT usage_date, tenant_id, SUM(data_mb) AS total_data_mb FROM usage_records WHERE tenant_id = :tenantId AND usage_date >= :fromDate AND usage_date < :toDate AND usage_type = 'DATA' GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build(),
            ParameterSpec.builder().name("toDate").label("To Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("usage_date").label("Date").type(ColumnSpec.ColumnType.DATE).semanticLabel("date").build(),
            ColumnSpec.builder().key("total_data_mb").label("Data (MB)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("data_mb").build()))
                .defaultSchedule("0 30 1 * * ?").owner("network-ops@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("usage_records"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("voice-usage-by-day").name("Voice Usage by Day")
                .description("Daily voice minutes per connection.")
                .category(ReportCategory.USAGE_BILLING).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT usage_date, tenant_id, SUM(voice_minutes) AS total_voice_min FROM usage_records WHERE tenant_id = :tenantId AND usage_date >= :fromDate AND usage_date < :toDate AND usage_type = 'VOICE' GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build(),
            ParameterSpec.builder().name("toDate").label("To Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("total_voice_min").label("Voice (min)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("voice_min").build()))
                .defaultSchedule("0 30 1 * * ?").owner("network-ops@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("usage_records"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("sms-usage-by-day").name("SMS Usage by Day")
                .description("Daily SMS count per connection.")
                .category(ReportCategory.USAGE_BILLING).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT usage_date, tenant_id, COUNT(*) AS sms_count FROM usage_records WHERE tenant_id = :tenantId AND usage_date >= :fromDate AND usage_date < :toDate AND usage_type = 'SMS' GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build(),
            ParameterSpec.builder().name("toDate").label("To Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("sms_count").label("SMS Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("sms_count").build()))
                .defaultSchedule("0 30 1 * * ?").owner("network-ops@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("usage_records"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("roaming-usage-summary").name("Roaming Usage Summary")
                .description("Outbound roaming usage by zone.")
                .category(ReportCategory.USAGE_BILLING).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT usage_date, roaming_zone, SUM(data_mb) AS roaming_data_mb, SUM(voice_minutes) AS roaming_voice_min FROM usage_records WHERE tenant_id = :tenantId AND is_roaming = 1 AND usage_date >= :fromDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("roaming_zone").label("Roaming Zone").type(ColumnSpec.ColumnType.STRING).semanticLabel("roaming_zone").build(),
            ColumnSpec.builder().key("roaming_data_mb").label("Data (MB)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("data_mb").build(),
            ColumnSpec.builder().key("roaming_voice_min").label("Voice (min)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("voice_min").build()))
                .defaultSchedule("0 30 2 * * ?").owner("network-ops@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("usage_records"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("bundle-consumption-rate").name("Bundle Consumption Rate")
                .description("% of bundle consumed per day.")
                .category(ReportCategory.USAGE_BILLING).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT usage_date, bundle_id, ROUND(100.0*SUM(used_units)/NULLIF(SUM(total_units),0),2) AS consumption_pct FROM bundle_usage WHERE tenant_id = :tenantId AND usage_date >= :fromDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("consumption_pct").label("Consumption %").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("consumption_pct").build()))
                .defaultSchedule("0 0 2 * * ?").owner("network-ops@selfcare.com").freshnessSlaMinutes(180)
                .dataSourceTables(List.of("bundle_usage"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("top-consumed-products").name("Top Consumed Products")
                .description("Most-used bundles by revenue.")
                .category(ReportCategory.PRODUCTS_OFFERS).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT p.name AS product_name, COUNT(DISTINCT u.connection_id) AS active_users, SUM(p.price_lkr) AS revenue_lkr FROM bundle_usage u JOIN products p ON p.id = u.bundle_id WHERE u.tenant_id = :tenantId AND u.usage_date >= :fromDate GROUP BY 1 ORDER BY 3 DESC LIMIT 50")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("product_name").label("Product").type(ColumnSpec.ColumnType.STRING).semanticLabel("product_name").build(),
            ColumnSpec.builder().key("revenue_lkr").label("Revenue (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("revenue_lkr").build()))
                .defaultSchedule("0 0 2 * * ?").owner("product-team@selfcare.com").freshnessSlaMinutes(180)
                .dataSourceTables(List.of("bundle_usage", "products"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("data-allowance-exhaustion-daily").name("Data Allowance Exhaustion (Daily)")
                .description("Connections that exhausted data allowance.")
                .category(ReportCategory.USAGE_BILLING).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT usage_date, COUNT(DISTINCT connection_id) AS exhausted_connections FROM usage_records WHERE tenant_id = :tenantId AND is_exhausted = 1 AND usage_date >= :fromDate GROUP BY 1 ORDER BY 1")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("exhausted_connections").label("Exhausted Connections").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("exhausted_connections").build()))
                .defaultSchedule("0 0 2 * * ?").owner("network-ops@selfcare.com").freshnessSlaMinutes(180)
                .dataSourceTables(List.of("usage_records"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("daily-revenue").name("Daily Revenue")
                .description("Total billed amount per day.")
                .category(ReportCategory.PAYMENT_RECHARGE).requiredPermission("REPORT_FINANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT DATE(billing_date) AS rdate, tenant_id, SUM(amount_lkr) AS total_revenue_lkr, COUNT(*) AS transaction_count FROM billing_transactions WHERE tenant_id = :tenantId AND billing_date >= :fromDate AND billing_date < :toDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build(),
            ParameterSpec.builder().name("toDate").label("To Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("rdate").label("Date").type(ColumnSpec.ColumnType.DATE).semanticLabel("date").build(),
            ColumnSpec.builder().key("total_revenue_lkr").label("Total Revenue (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("revenue_lkr").build(),
            ColumnSpec.builder().key("transaction_count").label("Transaction Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("transaction_count").build()))
                .defaultSchedule("0 15 1 * * ?").owner("finance@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("billing_transactions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("revenue-by-product").name("Revenue by Product/Category")
                .description("Revenue breakdown by product category.")
                .category(ReportCategory.PRODUCTS_OFFERS).requiredPermission("REPORT_FINANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT p.category, SUM(bt.amount_lkr) AS revenue_lkr, COUNT(*) AS tx_count FROM billing_transactions bt JOIN products p ON p.id = bt.product_id WHERE bt.tenant_id = :tenantId AND bt.billing_date >= :fromDate GROUP BY 1 ORDER BY 2 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("category").label("Category").type(ColumnSpec.ColumnType.STRING).semanticLabel("category").build(),
            ColumnSpec.builder().key("revenue_lkr").label("Revenue (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("revenue_lkr").build()))
                .defaultSchedule("0 15 1 * * ?").owner("finance@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("billing_transactions", "products"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("arpu-by-day").name("ARPU by Day")
                .description("Average Revenue Per User per day.")
                .category(ReportCategory.PAYMENT_RECHARGE).requiredPermission("REPORT_FINANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT bt.billing_date AS rdate, bt.tenant_id, ROUND(SUM(bt.amount_lkr)/NULLIF(COUNT(DISTINCT bt.user_id),0),2) AS arpu_lkr FROM billing_transactions bt WHERE bt.tenant_id = :tenantId AND bt.billing_date >= :fromDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("arpu_lkr").label("ARPU (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("arpu_lkr").build()))
                .defaultSchedule("0 15 1 * * ?").owner("finance@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("billing_transactions", "accounts"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("outstanding-amounts").name("Outstanding Amounts")
                .description("Total unpaid bills.")
                .category(ReportCategory.PAYMENT_RECHARGE).requiredPermission("REPORT_FINANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT tenant_id, COUNT(*) AS overdue_invoices, SUM(outstanding_lkr) AS total_outstanding_lkr, AVG(DATEDIFF(NOW(), due_date)) AS avg_overdue_days FROM billing_invoices WHERE tenant_id = :tenantId AND status = 'OVERDUE' GROUP BY 1")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("total_outstanding_lkr").label("Outstanding (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("revenue_lkr").build(),
            ColumnSpec.builder().key("overdue_invoices").label("Overdue Invoices").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 0 2 * * ?").owner("finance@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("billing_invoices"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("payment-method-distribution").name("Payment Method Distribution")
                .description("Revenue by payment channel.")
                .category(ReportCategory.PAYMENT_RECHARGE).requiredPermission("REPORT_FINANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT payment_channel, SUM(amount_lkr) AS revenue_lkr, COUNT(*) AS tx_count FROM billing_transactions WHERE tenant_id = :tenantId AND billing_date >= :fromDate GROUP BY 1 ORDER BY 2 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("payment_channel").label("Payment Channel").type(ColumnSpec.ColumnType.STRING).semanticLabel("channel").build(),
            ColumnSpec.builder().key("revenue_lkr").label("Revenue (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("revenue_lkr").build()))
                .defaultSchedule("0 15 1 * * ?").owner("finance@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("billing_transactions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("recharge-history").name("Recharge History")
                .description("All recharge transactions.")
                .category(ReportCategory.PAYMENT_RECHARGE).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT recharge_date, tenant_id, connection_id, amount_lkr, payment_method, status FROM recharge_transactions WHERE tenant_id = :tenantId AND recharge_date >= :fromDate ORDER BY 1 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("amount_lkr").label("Amount (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("amount_lkr").build(),
            ColumnSpec.builder().key("status").label("Status").type(ColumnSpec.ColumnType.STRING).semanticLabel("status").build()))
                .defaultSchedule("0 0 2 * * ?").owner("finance@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("recharge_transactions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("revenue-by-lob").name("Revenue by Line of Business")
                .description("Revenue segmented by LOB/type.")
                .category(ReportCategory.PAYMENT_RECHARGE).requiredPermission("REPORT_FINANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT a.lob, SUM(bt.amount_lkr) AS revenue_lkr FROM billing_transactions bt JOIN accounts a ON a.id = bt.account_id WHERE bt.tenant_id = :tenantId AND bt.billing_date >= :fromDate GROUP BY 1 ORDER BY 2 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("lob").label("LOB").type(ColumnSpec.ColumnType.STRING).semanticLabel("lob").build(),
            ColumnSpec.builder().key("revenue_lkr").label("Revenue (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("revenue_lkr").build()))
                .defaultSchedule("0 15 1 * * ?").owner("finance@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("billing_transactions", "accounts"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("revenue-forecast").name("Revenue Forecast")
                .description("Simple trending forecast.")
                .category(ReportCategory.CUSTOMER_ADOPTION).requiredPermission("REPORT_FINANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT DATE(billing_date) AS rdate, SUM(amount_lkr) AS revenue_lkr FROM billing_transactions WHERE tenant_id = :tenantId AND billing_date >= DATE_SUB(:fromDate, INTERVAL 30 DAY) GROUP BY 1 ORDER BY 1")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("revenue_lkr").label("Revenue (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("revenue_lkr").build()))
                .defaultSchedule("0 0 3 * * ?").owner("finance@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("billing_transactions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("arpu-trend").name("ARPU Trend")
                .description("Monthly ARPU over time.")
                .category(ReportCategory.CUSTOMER_ADOPTION).requiredPermission("REPORT_FINANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT year(billing_date)*100+month(billing_date) AS month_id, tenant_id, ROUND(SUM(amount_lkr)/NULLIF(COUNT(DISTINCT user_id),0),2) AS arpu_lkr FROM billing_transactions WHERE tenant_id = :tenantId GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("arpu_lkr").label("ARPU (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("arpu_lkr").build()))
                .defaultSchedule("0 0 3 * * ?").owner("finance@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("billing_transactions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("customer-lifetime-value").name("Customer Lifetime Value")
                .description("Estimated LTV per customer segment.")
                .category(ReportCategory.CUSTOMER_ADOPTION).requiredPermission("REPORT_FINANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT a.segment, COUNT(DISTINCT bt.user_id) AS customers, SUM(bt.amount_lkr)/NULLIF(COUNT(DISTINCT bt.user_id),0) AS avg_ltv_lkr FROM billing_transactions bt JOIN accounts a ON a.id = bt.account_id WHERE bt.tenant_id = :tenantId GROUP BY 1 ORDER BY 3 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("avg_ltv_lkr").label("Avg LTV (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("revenue_lkr").build()))
                .defaultSchedule("0 0 7 * * ?").owner("finance@selfcare.com").freshnessSlaMinutes(480)
                .dataSourceTables(List.of("billing_transactions", "accounts"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("churn-rate").name("Churn Rate")
                .description("Monthly connection churn rate.")
                .category(ReportCategory.CUSTOMER_ADOPTION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT year(deactivation_date)*100+month(deactivation_date) AS month_id, tenant_id, COUNT(*) AS churned_connections, ROUND(COUNT(*)*100.0/NULLIF((SELECT COUNT(*) FROM accounts a2 WHERE a2.tenant_id = accounts.tenant_id),0),2) AS churn_rate FROM accounts WHERE tenant_id = :tenantId AND status = 'DEACTIVATED' AND deactivation_date >= :fromDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("churn_rate").label("Churn Rate %").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("churn_rate").build()))
                .defaultSchedule("0 0 3 * * ?").owner("finance@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("accounts"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("revenue-per-service").name("Revenue per Service")
                .description("Revenue per service type.")
                .category(ReportCategory.USAGE_BILLING).requiredPermission("REPORT_FINANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT service_type, SUM(amount_lkr) AS revenue_lkr, COUNT(*) AS tx_count FROM billing_transactions WHERE tenant_id = :tenantId AND billing_date >= :fromDate GROUP BY 1 ORDER BY 2 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("service_type").label("Service Type").type(ColumnSpec.ColumnType.STRING).semanticLabel("category").build(),
            ColumnSpec.builder().key("revenue_lkr").label("Revenue (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("revenue_lkr").build()))
                .defaultSchedule("0 0 3 * * ?").owner("finance@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("billing_transactions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("failed-login-attempts").name("Failed Login Attempts")
                .description("Security: brute-force detection.")
                .category(ReportCategory.SECURITY_AUDIT).requiredPermission("REPORT_SECURITY")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT event_time, tenant_id, user_id, ip_address, failure_reason FROM auth_events WHERE tenant_id = :tenantId AND event_type = 'LOGIN_FAILED' AND event_time >= :fromDate ORDER BY 1 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("failure_reason").label("Failure Reason").type(ColumnSpec.ColumnType.STRING).semanticLabel("failure_reason").build()))
                .defaultSchedule("0 0 * * * ?").owner("security@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("auth_events"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("suspicious-otp-usage").name("Suspicious OTP Usage")
                .description("OTP abuse and bot detection.")
                .category(ReportCategory.SECURITY_AUDIT).requiredPermission("REPORT_SECURITY")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT DATE(created_at) AS rdate, tenant_id, user_id, COUNT(*) AS otp_attempts, COUNT(DISTINCT ip_address) AS unique_ips FROM otp_events WHERE tenant_id = :tenantId AND created_at >= :fromDate GROUP BY 1,2,3 HAVING COUNT(*) > 5 OR COUNT(DISTINCT ip_address) > 3 ORDER BY 1 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("otp_attempts").label("OTP Attempts").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 0 * * * ?").owner("security@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("otp_events"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("unusual-data-usage").name("Unusual Data Usage")
                .description("Data usage anomaly detection.")
                .category(ReportCategory.SECURITY_AUDIT).requiredPermission("REPORT_SECURITY")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT usage_date, connection_id, SUM(data_mb) AS total_mb FROM usage_records WHERE tenant_id = :tenantId AND usage_date >= :fromDate GROUP BY 1,2 HAVING SUM(data_mb) > 5000 ORDER BY 1 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("total_mb").label("Total MB").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("data_mb").build()))
                .defaultSchedule("0 0 2 * * ?").owner("security@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("usage_records"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("payment-anomaly").name("Payment Anomaly")
                .description("Suspicious payment patterns.")
                .category(ReportCategory.SECURITY_AUDIT).requiredPermission("REPORT_SECURITY")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT billing_date, user_id, amount_lkr, COUNT(*) AS tx_count FROM billing_transactions WHERE tenant_id = :tenantId AND billing_date >= :fromDate GROUP BY 1,2,3 HAVING COUNT(*) > 3 AND amount_lkr > 10000 ORDER BY 1 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("amount_lkr").label("Amount (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("amount_lkr").build(),
            ColumnSpec.builder().key("tx_count").label("Tx Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 0 2 * * ?").owner("security@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("billing_transactions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("account-takeover-signals").name("Account Takeover Signals")
                .description("Multiple IPs/devices per account.")
                .category(ReportCategory.SECURITY_AUDIT).requiredPermission("REPORT_SECURITY")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT date(start_time) AS rdate, user_id, COUNT(DISTINCT ip_address) AS unique_ips, COUNT(DISTINCT device_id) AS unique_devices FROM sessions WHERE tenant_id = :tenantId AND start_time >= :fromDate GROUP BY 1,2 HAVING COUNT(DISTINCT ip_address) > 5 ORDER BY 1 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("unique_ips").label("Unique IPs").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build(),
            ColumnSpec.builder().key("unique_devices").label("Unique Devices").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 0 3 * * ?").owner("security@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("sessions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("api-latency-summary").name("API Latency Summary")
                .description("P50/P95/P99 latency per service.")
                .category(ReportCategory.API_PROVIDER_OPS).requiredPermission("REPORT_SRE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT timestamp, service, AVG(latency_ms) AS p50_ms FROM api_metrics WHERE tenant_id = :tenantId AND timestamp >= :fromDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("p50_ms").label("P50 (ms)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("p50_ms").build()))
                .defaultSchedule("0 5 * * * ?").owner("sre@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("api_metrics"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("error-rate-by-service").name("Error Rate by Service")
                .description("5xx error rate per service.")
                .category(ReportCategory.API_PROVIDER_OPS).requiredPermission("REPORT_SRE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT timestamp, service, ROUND(SUM(error_count)*100.0/NULLIF(SUM(total_count),0),2) AS error_rate FROM api_metrics WHERE tenant_id = :tenantId AND timestamp >= :fromDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("error_rate").label("Error Rate %").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("failure_rate").build()))
                .defaultSchedule("0 5 * * * ?").owner("sre@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("api_metrics"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("provider-health-summary").name("Provider Health Summary")
                .description("Provider uptime and latency.")
                .category(ReportCategory.API_PROVIDER_OPS).requiredPermission("REPORT_SRE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT timestamp, provider, success_count, failure_count, avg_latency_ms, ROUND(failure_count*100.0/NULLIF((success_count+failure_count),0),2) AS error_rate FROM provider_metrics WHERE tenant_id = :tenantId AND timestamp >= :fromDate ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("error_rate").label("Error Rate %").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("failure_rate").build(),
            ColumnSpec.builder().key("avg_latency_ms").label("Avg Latency (ms)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("latency_ms").build()))
                .defaultSchedule("0 5 * * * ?").owner("sre@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("provider_metrics"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("db-connection-pool-stats").name("DB Connection Pool Stats")
                .description("Pool utilization per service.")
                .category(ReportCategory.API_PROVIDER_OPS).requiredPermission("REPORT_SRE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT timestamp, service, active_connections, max_connections, ROUND(active_connections*100.0/NULLIF(max_connections,0),2) AS utilization_pct FROM infra_metrics WHERE tenant_id = :tenantId AND timestamp >= :fromDate ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("utilization_pct").label("Utilization %").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("utilization_pct").build()))
                .defaultSchedule("0 0 * * * ?").owner("sre@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("infra_metrics"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("kafka-consumer-lag").name("Kafka Consumer Lag")
                .description("Consumer group lag.")
                .category(ReportCategory.API_PROVIDER_OPS).requiredPermission("REPORT_SRE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT timestamp, consumer_group, topic, lag_messages FROM kafka_metrics WHERE tenant_id = :tenantId AND timestamp >= :fromDate ORDER BY 1,2,3")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("lag_messages").label("Lag Messages").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 5 * * * ?").owner("sre@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("kafka_metrics"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("config-publish-summary").name("Config Publish Summary")
                .description("Successful/failed config publishes.")
                .category(ReportCategory.SECURITY_AUDIT).requiredPermission("REPORT_SRE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT DATE(published_at) AS rdate, tenant_id, environment, COUNT(*) AS publish_count, SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END) AS success_count FROM config_events WHERE tenant_id = :tenantId AND published_at >= :fromDate GROUP BY 1,2,3 ORDER BY 1,2,3")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("publish_count").label("Publish Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build(),
            ColumnSpec.builder().key("success_count").label("Success Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("success_count").build()))
                .defaultSchedule("0 0 2 * * ?").owner("sre@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("config_events"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("audit-log-summary").name("Audit Log Summary")
                .description("All admin actions for compliance review.")
                .category(ReportCategory.SECURITY_AUDIT).requiredPermission("REPORT_COMPLIANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT event_time, tenant_id, actor, action, resource_type, resource_id, status FROM audit_logs WHERE tenant_id = :tenantId AND event_time >= :fromDate ORDER BY 1 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("action").label("Action").type(ColumnSpec.ColumnType.STRING).semanticLabel("action").build(),
            ColumnSpec.builder().key("status").label("Status").type(ColumnSpec.ColumnType.STRING).semanticLabel("status").build()))
                .defaultSchedule("0 0 2 * * ?").owner("compliance@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("audit_logs"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("pii-access-log").name("PII Access Log")
                .description("Sensitive data access for GDPR.")
                .category(ReportCategory.SECURITY_AUDIT).requiredPermission("REPORT_COMPLIANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT event_time, tenant_id, actor, action, resource_type, resource_id FROM audit_logs WHERE tenant_id = :tenantId AND event_time >= :fromDate AND contains_pii = 1 ORDER BY 1 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("actor").label("Actor").type(ColumnSpec.ColumnType.STRING).semanticLabel("actor").build(),
            ColumnSpec.builder().key("action").label("Action").type(ColumnSpec.ColumnType.STRING).semanticLabel("action").build()))
                .defaultSchedule("0 0 2 * * ?").owner("compliance@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("audit_logs"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("data-retention-status").name("Data Retention Status")
                .description("Records past retention period.")
                .category(ReportCategory.SECURITY_AUDIT).requiredPermission("REPORT_COMPLIANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT tenant_id, data_class, COUNT(*) AS records_to_delete FROM audit_logs WHERE tenant_id = :tenantId AND is_deleted = 0 GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("records_to_delete").label("Records to Delete").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 0 7 * * ?").owner("compliance@selfcare.com").freshnessSlaMinutes(480)
                .dataSourceTables(List.of("audit_logs"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("gdpr-data-subject-requests").name("GDPR SAR Requests")
                .description("Outstanding SAR requests.")
                .category(ReportCategory.SECURITY_AUDIT).requiredPermission("REPORT_COMPLIANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT request_id, tenant_id, customer_id, request_type, status, created_at, due_date, DATEDIFF(due_date, NOW()) AS days_remaining FROM privacy_requests WHERE tenant_id = :tenantId AND status IN ('PENDING','IN_PROGRESS') ORDER BY due_date ASC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("days_remaining").label("Days Remaining").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("days_remaining").build(),
            ColumnSpec.builder().key("status").label("Status").type(ColumnSpec.ColumnType.STRING).semanticLabel("status").build()))
                .defaultSchedule("0 0 1 * * ?").owner("compliance@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("privacy_requests"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("regulatory-report").name("Regulatory Report")
                .description("Operator regulatory compliance report.")
                .category(ReportCategory.SECURITY_AUDIT).requiredPermission("REPORT_COMPLIANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT tenant_id, COUNT(DISTINCT user_id) AS total_customers, SUM(amount_lkr) AS total_revenue_lkr FROM billing_transactions bt JOIN accounts a ON a.id = bt.account_id WHERE bt.tenant_id = :tenantId GROUP BY 1")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("total_customers").label("Total Customers").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build(),
            ColumnSpec.builder().key("total_revenue_lkr").label("Total Revenue (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("revenue_lkr").build()))
                .defaultSchedule("0 0 30 * * ?").owner("compliance@selfcare.com").freshnessSlaMinutes(1440)
                .dataSourceTables(List.of("billing_transactions", "accounts"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("journey-completion-rate").name("Journey Completion Rate")
                .description("Completion rate per journey type.")
                .category(ReportCategory.CAMPAIGN_NOTIFICATION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT journey_type, DATE(completed_at) AS rdate, COUNT(*) AS total_started, SUM(CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END) AS completed, ROUND(SUM(CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END)*100.0/NULLIF(COUNT(*),0),2) AS completion_rate FROM journey_instances WHERE tenant_id = :tenantId AND started_at >= :fromDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("completion_rate").label("Completion Rate %").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("success_rate").build()))
                .defaultSchedule("0 0 2 * * ?").owner("product-team@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("journey_instances"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("journey-drop-off-points").name("Journey Drop-off Points")
                .description("Step where users abandon journeys.")
                .category(ReportCategory.CAMPAIGN_NOTIFICATION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT journey_type, failed_step, COUNT(*) AS drop_off_count FROM journey_instances WHERE tenant_id = :tenantId AND status = 'ABANDONED' AND started_at >= :fromDate GROUP BY 1,2 ORDER BY 3 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("drop_off_count").label("Drop-off Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 0 2 * * ?").owner("product-team@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("journey_instances"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("journey-avg-duration").name("Journey Average Duration")
                .description("Time from start to completion/abandon.")
                .category(ReportCategory.CAMPAIGN_NOTIFICATION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT journey_type, COUNT(*) AS total FROM journey_instances WHERE tenant_id = :tenantId AND started_at >= :fromDate GROUP BY 1 ORDER BY 2 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("total").label("Total").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 0 2 * * ?").owner("product-team@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("journey_instances"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("journey-conversion-by-channel").name("Journey Conversion by Channel")
                .description("Conversion rate per channel.")
                .category(ReportCategory.CAMPAIGN_NOTIFICATION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT channel, journey_type, COUNT(*) AS total, SUM(CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END) AS converted, ROUND(SUM(CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END)*100.0/NULLIF(COUNT(*),0),2) AS conversion_rate FROM journey_instances WHERE tenant_id = :tenantId AND started_at >= :fromDate GROUP BY 1,2 ORDER BY 3 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("conversion_rate").label("Conversion Rate %").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("success_rate").build()))
                .defaultSchedule("0 0 2 * * ?").owner("marketing-team@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("journey_instances"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("journey-retry-analysis").name("Journey Retry Analysis")
                .description("Re-attempt patterns after failure.")
                .category(ReportCategory.CAMPAIGN_NOTIFICATION).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT journey_type, COUNT(*) AS retry_count, AVG(attempt_number) AS avg_attempts FROM journey_instances WHERE tenant_id = :tenantId AND status IN ('COMPLETED','ABANDONED') AND started_at >= :fromDate GROUP BY 1 ORDER BY 2 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("retry_count").label("Retry Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build(),
            ColumnSpec.builder().key("avg_attempts").label("Avg Attempts").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 0 3 * * ?").owner("product-team@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("journey_instances"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("campaign-effectiveness").name("Campaign Effectiveness")
                .description("Conversion rate per campaign.")
                .category(ReportCategory.CAMPAIGN_NOTIFICATION).requiredPermission("REPORT_MARKETING")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT campaign_id, campaign_name, COUNT(*) AS impressions, SUM(CASE WHEN event_type='CONVERSION' THEN 1 ELSE 0 END) AS conversions, ROUND(SUM(CASE WHEN event_type='CONVERSION' THEN 1 ELSE 0 END)*100.0/NULLIF(COUNT(*),0),2) AS conversion_rate FROM campaign_events WHERE tenant_id = :tenantId AND event_time >= :fromDate GROUP BY 1,2 ORDER BY 2 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("conversions").label("Conversions").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build(),
            ColumnSpec.builder().key("conversion_rate").label("Conversion Rate %").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("success_rate").build()))
                .defaultSchedule("0 0 2 * * ?").owner("marketing-team@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("campaign_events"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("push-notification-stats").name("Push Notification Stats")
                .description("Delivery and open rates.")
                .category(ReportCategory.CAMPAIGN_NOTIFICATION).requiredPermission("REPORT_MARKETING")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT DATE(sent_at) AS rdate, notification_type, COUNT(*) AS sent, SUM(CASE WHEN status='DELIVERED' THEN 1 ELSE 0 END) AS delivered, SUM(CASE WHEN event_type='OPENED' THEN 1 ELSE 0 END) AS opened FROM notification_events WHERE tenant_id = :tenantId AND sent_at >= :fromDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("sent").label("Sent").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build(),
            ColumnSpec.builder().key("delivered").label("Delivered").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("success_count").build(),
            ColumnSpec.builder().key("opened").label("Opened").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 0 2 * * ?").owner("marketing-team@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("notification_events"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("offer-conversion-rate").name("Offer Conversion Rate")
                .description("Acceptance rate for offered upgrades.")
                .category(ReportCategory.CAMPAIGN_NOTIFICATION).requiredPermission("REPORT_MARKETING")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT offer_type, COUNT(*) AS offers_sent, SUM(CASE WHEN status='ACCEPTED' THEN 1 ELSE 0 END) AS accepted, ROUND(SUM(CASE WHEN status='ACCEPTED' THEN 1 ELSE 0 END)*100.0/NULLIF(COUNT(*),0),2) AS conversion_rate FROM offer_events WHERE tenant_id = :tenantId AND created_at >= :fromDate GROUP BY 1 ORDER BY 2 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("accepted").label("Accepted").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("success_count").build(),
            ColumnSpec.builder().key("conversion_rate").label("Conversion Rate %").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("success_rate").build()))
                .defaultSchedule("0 0 2 * * ?").owner("marketing-team@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("offer_events"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("feature-flag-exposure").name("Feature Flag Exposure")
                .description("Flag exposure by variant.")
                .category(ReportCategory.CAMPAIGN_NOTIFICATION).requiredPermission("REPORT_MARKETING")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT flag_key, variant, COUNT(DISTINCT user_id) AS exposed_users, COUNT(*) AS total_exposures FROM feature_flag_events WHERE tenant_id = :tenantId AND event_time >= :fromDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("exposed_users").label("Exposed Users").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("users").build(),
            ColumnSpec.builder().key("total_exposures").label("Total Exposures").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 0 2 * * ?").owner("product-team@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("feature_flag_events"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("ab-test-results").name("A/B Test Results")
                .description("Conversion by experiment group.")
                .category(ReportCategory.CAMPAIGN_NOTIFICATION).requiredPermission("REPORT_MARKETING")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT experiment_id, variant, COUNT(DISTINCT user_id) AS users, SUM(CASE WHEN outcome='CONVERTED' THEN 1 ELSE 0 END) AS conversions, ROUND(SUM(CASE WHEN outcome='CONVERTED' THEN 1 ELSE 0 END)*100.0/NULLIF(COUNT(DISTINCT user_id),0),2) AS conversion_rate FROM ab_test_events WHERE tenant_id = :tenantId AND event_time >= :fromDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("conversion_rate").label("Conversion Rate %").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("success_rate").build()))
                .defaultSchedule("0 0 3 * * ?").owner("marketing-team@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("ab_test_events"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("policy-coverage-summary").name("Policy Coverage Summary")
                .description("Active policies by type and status.")
                .category(ReportCategory.AI).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT policy_type, status, COUNT(*) AS policy_count, SUM(premium_lkr) AS total_premium_lkr FROM insurance_policies WHERE tenant_id = :tenantId AND status IN ('ACTIVE','EXPIRED','CANCELLED') AND effective_date >= :fromDate GROUP BY 1,2 ORDER BY 3 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("policy_count").label("Policy Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build(),
            ColumnSpec.builder().key("total_premium_lkr").label("Total Premium (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("revenue_lkr").build()))
                .defaultSchedule("0 0 2 * * ?").owner("insurance-ops@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("insurance_policies"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("claims-summary").name("Claims Summary")
                .description("Claims by status and type.")
                .category(ReportCategory.AI).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT claim_type, status, COUNT(*) AS claim_count, SUM(claimed_amount_lkr) AS total_claimed_lkr, SUM(approved_amount_lkr) AS total_approved_lkr FROM insurance_claims WHERE tenant_id = :tenantId AND submitted_at >= :fromDate GROUP BY 1,2 ORDER BY 2,3 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("claim_count").label("Claim Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build(),
            ColumnSpec.builder().key("total_claimed_lkr").label("Total Claimed (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("amount_lkr").build(),
            ColumnSpec.builder().key("total_approved_lkr").label("Total Approved (LKR)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("amount_lkr").build()))
                .defaultSchedule("0 0 2 * * ?").owner("insurance-ops@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("insurance_claims"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("claim-settlement-time").name("Claim Settlement Time")
                .description("Average days to settle claims.")
                .category(ReportCategory.AI).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT claim_type, ROUND(AVG(DATEDIFF(resolved_at, submitted_at)),1) AS avg_settlement_days, COUNT(*) AS resolved_claims FROM insurance_claims WHERE tenant_id = :tenantId AND resolved_at IS NOT NULL AND submitted_at >= :fromDate GROUP BY 1 ORDER BY 2 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("avg_settlement_days").label("Avg Settlement Days").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("days").build(),
            ColumnSpec.builder().key("resolved_claims").label("Resolved Claims").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 0 3 * * ?").owner("insurance-ops@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("insurance_claims"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("premium-collection-rate").name("Premium Collection Rate")
                .description("Collection rate by product.")
                .category(ReportCategory.AI).requiredPermission("REPORT_FINANCE")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT policy_type, COUNT(*) AS total_policies, SUM(CASE WHEN premium_paid_lkr >= premium_due_lkr THEN 1 ELSE 0 END) AS fully_paid, ROUND(SUM(CASE WHEN premium_paid_lkr >= premium_due_lkr THEN 1 ELSE 0 END)*100.0/NULLIF(COUNT(*),0),2) AS collection_rate FROM insurance_policies WHERE tenant_id = :tenantId AND effective_date >= :fromDate GROUP BY 1 ORDER BY 1")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("collection_rate").label("Collection Rate %").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("success_rate").build()))
                .defaultSchedule("0 0 2 * * ?").owner("finance@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("insurance_policies"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("beneficiary-portfolio").name("Beneficiary Portfolio")
                .description("Beneficiary coverage summary.")
                .category(ReportCategory.AI).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT tenant_id, COUNT(DISTINCT policyholder_id) AS policyholders, COUNT(DISTINCT beneficiary_id) AS beneficiaries FROM insurance_beneficiaries WHERE tenant_id = :tenantId GROUP BY 1")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("policyholders").label("Policyholders").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build(),
            ColumnSpec.builder().key("beneficiaries").label("Beneficiaries").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 0 7 * * ?").owner("insurance-ops@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("insurance_beneficiaries"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("ai-usage-summary").name("AI Usage Summary")
                .description("Chat volume, cost, tokens by use case.")
                .category(ReportCategory.AI).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT DATE(created_at) AS rdate, use_case, COUNT(*) AS chat_count, SUM(tokens_used) AS total_tokens, SUM(cost_usd) AS total_cost_usd FROM ai_chat_sessions WHERE tenant_id = :tenantId AND created_at >= :fromDate GROUP BY 1,2 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("chat_count").label("Chat Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build(),
            ColumnSpec.builder().key("total_tokens").label("Total Tokens").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("tokens_used").build(),
            ColumnSpec.builder().key("total_cost_usd").label("Total Cost (USD)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("cost_usd").build()))
                .defaultSchedule("0 30 * * * ?").owner("ai-platform@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("ai_chat_sessions"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("ai-latency-by-model").name("AI Latency by Model")
                .description("P95 latency per model.")
                .category(ReportCategory.AI).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT DATE(call_time) AS rdate, model_name, model_version, ROUND(AVG(latency_ms),1) AS avg_latency_ms, COUNT(*) AS call_count FROM ai_model_calls WHERE tenant_id = :tenantId AND call_time >= :fromDate GROUP BY 1,2,3 ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("avg_latency_ms").label("Avg Latency (ms)").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("latency_ms").build(),
            ColumnSpec.builder().key("call_count").label("Call Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 30 * * * ?").owner("ai-platform@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("ai_model_calls"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("ai-safety-block-rate").name("AI Safety Block Rate")
                .description("Safety violations by category.")
                .category(ReportCategory.AI).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT DATE(event_time) AS rdate, violation_type, COUNT(*) AS block_count FROM ai_audit_events WHERE tenant_id = :tenantId AND event_type = 'SAFETY_BLOCK' AND event_time >= :fromDate GROUP BY 1,2 ORDER BY 1,3 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("block_count").label("Block Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("safety_block_count").build()))
                .defaultSchedule("0 30 * * * ?").owner("ai-platform@selfcare.com").freshnessSlaMinutes(60)
                .dataSourceTables(List.of("ai_audit_events"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("rag-retrieval-quality").name("RAG Retrieval Quality")
                .description("Average relevance score per knowledge source.")
                .category(ReportCategory.AI).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT knowledge_source, ROUND(AVG(relevance_score),3) AS avg_relevance, COUNT(*) AS query_count FROM rag_queries WHERE tenant_id = :tenantId AND query_time >= :fromDate GROUP BY 1 ORDER BY 2 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("avg_relevance").label("Avg Relevance").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("rag_relevance_score").build(),
            ColumnSpec.builder().key("query_count").label("Query Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build()))
                .defaultSchedule("0 0 2 * * ?").owner("ai-platform@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("rag_queries"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("ai-tool-usage").name("AI Tool Usage")
                .description("Tool call frequency and success rate.")
                .category(ReportCategory.AI).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT tool_name, COUNT(*) AS call_count, SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END) AS success_count, ROUND(SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END)*100.0/NULLIF(COUNT(*),0),2) AS success_rate FROM ai_tool_calls WHERE tenant_id = :tenantId AND call_time >= :fromDate GROUP BY 1 ORDER BY 2 DESC")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("call_count").label("Call Count").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("count").build(),
            ColumnSpec.builder().key("success_rate").label("Success Rate %").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("success_rate").build()))
                .defaultSchedule("0 0 2 * * ?").owner("ai-platform@selfcare.com").freshnessSlaMinutes(120)
                .dataSourceTables(List.of("ai_tool_calls"))
                .isAiAssisted(true).build(),

            ReportDefinition.builder().id("ai-evaluation-results").name("AI Evaluation Results")
                .description("Evaluation scores by use case.")
                .category(ReportCategory.AI).requiredPermission("REPORT_ANALYST")
                .query(QuerySpec.builder().type(QuerySpec.QueryType.SQL)
                    .sqlTemplate("SELECT evaluation_date, use_case, model_version, task_success_rate, hallucination_rate, injection_resistance_rate, policy_compliance_rate FROM ai_evaluation_results WHERE tenant_id = :tenantId AND evaluation_date >= :fromDate ORDER BY 1,2")
                    .parameters(List.of(
            ParameterSpec.builder().name("tenantId").label("Tenant").type(ParameterSpec.ParamType.TENANT).required(true).build(),
            ParameterSpec.builder().name("fromDate").label("From Date").type(ParameterSpec.ParamType.DATE).required(true).build())).build())
                .outputColumns(List.of(
            ColumnSpec.builder().key("task_success_rate").label("Task Success Rate").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("quality_score").build(),
            ColumnSpec.builder().key("hallucination_rate").label("Hallucination Rate").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("quality_score").build(),
            ColumnSpec.builder().key("policy_compliance_rate").label("Policy Compliance Rate").type(ColumnSpec.ColumnType.NUMBER).semanticLabel("quality_score").build()))
                .defaultSchedule("0 0 7 * * ?").owner("ai-platform@selfcare.com").freshnessSlaMinutes(240)
                .dataSourceTables(List.of("ai_evaluation_results"))
                .isAiAssisted(true).build()

        );
    }
}