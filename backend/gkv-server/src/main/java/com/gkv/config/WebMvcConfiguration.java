package com.gkv.config;

import com.gkv.interceptor.JwtTokenAdminInterceptor;
import com.gkv.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Parameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置类，注册web层相关组件
 */
@Configuration
@EnableSwagger2
@Slf4j          //配置类
public class WebMvcConfiguration extends WebMvcConfigurationSupport {//继承了父类

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;
    //注入用户端的拦截器对象
    @Autowired
    private JwtTokenUserInterceptor jwtTokenUserInterceptor;


    /**
     * 通过knife4j生成接口文档
     * @return
     */
    @Bean
    public Docket docket1() { //书写生成接口文档格式
        log.info("准备生成接口文档。。");
        ApiInfo apiInfo = new ApiInfoBuilder()
                .title("gkv项目接口文档")
                .version("2.0")
                .description("gkv项目接口文档")
                .build();
        Docket docket = new Docket(DocumentationType.SWAGGER_2)
                .groupName("管理端接口")
                .apiInfo(apiInfo)
                .select()
                //指定生成接口相关参数要扫描的包
                .apis(RequestHandlerSelectors.basePackage("com.gkv.controller.admin"))
                .paths(PathSelectors.any())
                .build()
                .globalOperationParameters(buildAdminParameters());
        return docket;
    }

    @Bean
    public Docket docket2() { //书写生成接口文档格式
        log.info("准备生成接口文档。。");
        ApiInfo apiInfo = new ApiInfoBuilder()
                .title("gkv项目接口文档")
                .version("2.0")
                .description("gkv项目接口文档")
                .build();
        Docket docket = new Docket(DocumentationType.SWAGGER_2)
                .groupName("用户端接口")
                .apiInfo(apiInfo)
                .select()
                //指定生成接口相关参数要扫描的包
                .apis(RequestHandlerSelectors.basePackage("com.gkv.controller.user"))
                .paths(PathSelectors.any())
                .build()
                .globalOperationParameters(buildGlobalParameters());
        return docket;
    }

    /**
     * 构建Swagger全局请求头参数，使用户在Swagger UI中可以输入Authentication令牌
     */
    private List<Parameter> buildGlobalParameters() {
        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new ParameterBuilder()
                .name("Authentication")
                .description("用户令牌")
                .modelRef(new ModelRef("string"))
                .parameterType("header")
                .required(false)
                .build());
        return parameters;
    }

    /**
     * 构建管理端Swagger全局请求头参数（token 为管理端令牌）
     */
    private List<Parameter> buildAdminParameters() {
        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new ParameterBuilder()
                .name("token")
                .description("管理端令牌")
                .modelRef(new ModelRef("string"))
                .parameterType("header")
                .required(false)
                .build());
        return parameters;
    }

    @Override
    protected void addCorsMappings(org.springframework.web.servlet.config.annotation.CorsRegistry registry) {
        log.info("配置跨域CORS...");
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600)
                .allowCredentials(true);
    }

    /**
     * 设置静态资源映射
     * @param registry
     */
    protected void addResourceHandlers(ResourceHandlerRegistry registry) { //将生成好的接口文档映射此路径，因此可在游览器访问
        log.info("开始设置静态资源映射...");
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    @Override
    protected void addInterceptors(InterceptorRegistry registry) {
        // 用户端拦截器：排除管理端路径，避免管理端 token（无 Authentication 头）被误拦截
        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/home/**",
                        "/ai/**",
                        "/travel/**",
                        "/admin/**",
                        "/doc.html",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/v2/api-docs",
                        "/v2/api-docs/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/favicon.ico",
                        "/error"
                );
        // 管理端拦截器：校验管理端 token + 权限点（登录接口放行）
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns(
                        "/admin/login",
                        "/doc.html",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/v2/api-docs",
                        "/v2/api-docs/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/favicon.ico",
                        "/error"
                );
    }

    /**
     * 扩展spring MVC框架的消息转换器
     */
    @Override
    protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {//该框架自带了8个消息转换器
        log.info("扩展消息转换器。。。");
        //创建一个消息转换器对象
        MappingJackson2HttpMessageConverter converter =new MappingJackson2HttpMessageConverter();
        //需要为消息转换器设置一个对象转换器，对象转换器可以将Java对象序列化为jason数据
        converter.setObjectMapper(new JacksonObjectMapper());
        //将自己的消息转换器加入容器中
        converters.add(0,converter); //让其排在第一位

    }

    /**
     * 注册 RestTemplate Bean，供 AgentHttpUtil 调用 AI 智能体服务。
     * 配置连接与读取超时：AI 生成行程耗时较长（实测 60s+），读取超时设 150s，
     * 避免公网链路慢时长连接被无限挂起。
     */
    @Bean
    public RestTemplate restTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);   // 连接超时 10s
        factory.setReadTimeout(150_000);     // 读取超时 150s（覆盖 Agent plan 60s+）
        return new RestTemplate(factory);
    }
}
