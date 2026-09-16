package com.myhive.backend.ai.graph;

import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Map;

/**
 * langgraph4j Studio: the planner graph as a diagram at {@code http://localhost:8080/index.html},
 * with a run panel that drives the very same {@link PlannerGraph#workflow()}.
 *
 * <p>Dev only, and doubly so. {@code @Profile("dev")} keeps it out of prod, and
 * {@code @ConditionalOnClass} pins it to {@code langgraph4j-studio-springboot} — the jar that carries
 * the web UI and that {@code build.gradle} keeps on {@code developmentOnly}, so it exists on the
 * bootRun classpath and nowhere else. This class registers the two servlets itself rather than
 * extending that jar's {@code LangGraphStudioConfig}: the superclass would have to be on
 * {@code compileClasspath}, which would drag its 30 MB {@code static/} bundle into the prod image.
 *
 * <p>The Studio runs on its own {@link MemorySaver}, never the application's saver — its threads are
 * throwaway experiments and have no business landing in the checkpoint table a real chat resumes from.
 */
@Configuration
@Profile("dev")
@ConditionalOnClass(name = "org.bsc.langgraph4j.studio.springboot.LangGraphStudioConfig")
public class PlannerStudioConfig {

    private static final String INSTANCE_ID = "planner";
    private static final String TITLE = "Trivlu AI planner";
    private static final String INIT_PATH = "/init";
    private static final String STREAM_PATH = "/stream/*";

    private final Map<String, LangGraphStudioServer.Instance> instances;

    public PlannerStudioConfig(PlannerGraph plannerGraph) {
        this.instances = Map.of(INSTANCE_ID, LangGraphStudioServer.Instance.builder()
                .title(TITLE)
                .graph(plannerGraph.workflow())
                .compileConfig(PlannerGraph.compileConfig(new MemorySaver()))
                .build());
    }

    @Bean
    public ServletRegistrationBean<LangGraphStudioServer.GraphInitServlet> studioInitServlet() {
        ServletRegistrationBean<LangGraphStudioServer.GraphInitServlet> bean =
                new ServletRegistrationBean<>(new LangGraphStudioServer.GraphInitServlet(instances), INIT_PATH);
        bean.setLoadOnStartup(1);
        return bean;
    }

    @Bean
    public ServletRegistrationBean<LangGraphStudioServer.GraphStreamServlet> studioStreamServlet() {
        ServletRegistrationBean<LangGraphStudioServer.GraphStreamServlet> bean =
                new ServletRegistrationBean<>(new LangGraphStudioServer.GraphStreamServlet(instances), STREAM_PATH);
        bean.setLoadOnStartup(1);
        return bean;
    }

    /**
     * The main chain ends in {@code anyRequest().authenticated()}, which would 401 every one of the
     * Studio's own URLs. This chain runs first and covers only them: the two servlets, and the root
     * level files the UI bundle is served from ({@code /index.html} and the hashed {@code .js} /
     * {@code .css} / {@code .svg} / {@code .map} siblings next to it). The API owns no root-level file
     * paths, and the whole class only exists under the dev profile with the Studio jar present.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain studioSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(INIT_PATH, "/stream/**", "/index.html", "/*.js", "/*.css", "/*.svg", "/*.map")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
