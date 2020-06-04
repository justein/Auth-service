package com.blueskykong.auth.config.oauth;

import com.blueskykong.auth.security.CustomAuthorizationTokenServices;
import com.blueskykong.auth.security.CustomRedisTokenStore;
import com.blueskykong.auth.security.CustomTokenEnhancer;
import com.blueskykong.auth.service.ClientSecretService;
import com.zaxxer.hikari.HikariDataSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.client.JdbcClientDetailsService;
import org.springframework.security.oauth2.provider.error.WebResponseExceptionTranslator;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.net.UnknownHostException;

/**通过EnableAuthorizationServer开启认证服务器的相关默认配置*/
/**通过继承 AuthorizationServerConfigurerAdapter 添加自定义配置*/
@Configuration
@EnableAuthorizationServer
public class OAuth2Config extends AuthorizationServerConfigurerAdapter {


    private AuthenticationManager authenticationManager;

    private WebResponseExceptionTranslator webResponseExceptionTranslator;

    private DataSource dataSource;

    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    public OAuth2Config(AuthenticationManager authenticationManager, WebResponseExceptionTranslator webResponseExceptionTranslator,
                        HikariDataSource dataSource, RedisConnectionFactory redisConnectionFactory) {
        this.authenticationManager = authenticationManager;
        this.webResponseExceptionTranslator = webResponseExceptionTranslator;
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    /**定义一个token存储器，可以选择其他*/
    @Bean
    public TokenStore tokenStore(RedisConnectionFactory redisConnectionFactory) {
        return new CustomRedisTokenStore(redisConnectionFactory);
    }

    @Bean
    public JdbcClientDetailsService clientDetailsService() {
        return new JdbcClientDetailsService(dataSource);
    }

    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
        //security.addTokenEndpointAuthenticationFilter(new CustomSecurityFilter());
        security
                .tokenKeyAccess("permitAll()") //获取token的请求，不进行拦截
                .checkTokenAccess("isAuthenticated()"); //检查token的请求，要进行验证
    }

    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        clients.withClientDetails(clientDetailsService());
    }

    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
        /**配置token的数据源，自定义tokenService等消息*/
        endpoints.authenticationManager(authenticationManager)
                .tokenStore(tokenStore(redisConnectionFactory))
                .tokenServices(authorizationServerTokenServices())
                .accessTokenConverter(accessTokenConverter())
                .exceptionTranslator(webResponseExceptionTranslator);
    }

    /**设置使用jwt进行token转换*/
    @Bean
    public JwtAccessTokenConverter accessTokenConverter() {
        JwtAccessTokenConverter converter = new CustomTokenEnhancer();
        converter.setSigningKey("secret");
        return converter;
    }

    @Bean
    public AuthorizationServerTokenServices authorizationServerTokenServices() {
        CustomAuthorizationTokenServices customTokenServices = new CustomAuthorizationTokenServices();
        customTokenServices.setTokenStore(tokenStore(redisConnectionFactory));
        customTokenServices.setSupportRefreshToken(true);
        customTokenServices.setReuseRefreshToken(false);
        /**存储client的详细信息，这里就相当于QQ存储的其他在QQ注册过‘通过qq登录’的应用的信息*/
        customTokenServices.setClientDetailsService(clientDetailsService());
        /**对token进行了一波加强，加入了自定义的UserId和clientId两个参数*/
        customTokenServices.setTokenEnhancer(accessTokenConverter());
        return customTokenServices;
    }

    @Configuration
    protected static class RedisConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "redisTemplate")
        public RedisTemplate<Object, Object> redisTemplate(
                RedisConnectionFactory redisConnectionFactory)
                throws UnknownHostException {
            RedisTemplate<Object, Object> template = new RedisTemplate<Object, Object>();
            template.setConnectionFactory(redisConnectionFactory);
            return template;
        }

        @Bean
        @ConditionalOnMissingBean(StringRedisTemplate.class)
        public StringRedisTemplate stringRedisTemplate(
                RedisConnectionFactory redisConnectionFactory)
                throws UnknownHostException {
            StringRedisTemplate template = new StringRedisTemplate();
            template.setConnectionFactory(redisConnectionFactory);
            return template;
        }
    }

    @Configuration
    @EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
    @MapperScan("com.blueskykong.auth.dao.mapper")
    public static class DatasourceConfig  {


    }


}
