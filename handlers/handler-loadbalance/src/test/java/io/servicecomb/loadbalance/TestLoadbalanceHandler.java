/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.servicecomb.loadbalance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response.Status;
import javax.xml.ws.Holder;

import org.apache.commons.configuration.AbstractConfiguration;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import com.netflix.config.DynamicPropertyFactory;
import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.Server;

import io.servicecomb.config.ConfigUtil;
import io.servicecomb.core.CseContext;
import io.servicecomb.core.Invocation;
import io.servicecomb.core.Transport;
import io.servicecomb.core.transport.TransportManager;
import io.servicecomb.foundation.common.cache.VersionedCache;
import io.servicecomb.foundation.test.scaffolding.config.ArchaiusUtils;
import io.servicecomb.loadbalance.filter.SimpleTransactionControlFilter;
import io.servicecomb.serviceregistry.RegistryUtils;
import io.servicecomb.serviceregistry.ServiceRegistry;
import io.servicecomb.serviceregistry.api.registry.MicroserviceInstance;
import io.servicecomb.serviceregistry.cache.CacheEndpoint;
import io.servicecomb.serviceregistry.cache.InstanceCacheManager;
import io.servicecomb.swagger.invocation.AsyncResponse;
import io.servicecomb.swagger.invocation.Response;
import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;

/**
 *
 *
 */
public class TestLoadbalanceHandler {
  String microserviceName = "ms";

  IRule rule = Mockito.mock(IRule.class);

  LoadbalanceHandler handler = new LoadbalanceHandler();

  Map<String, LoadBalancer> loadBalancerMap = Deencapsulation.getField(handler, "loadBalancerMap");

  private LoadBalancer loadBalancer = new LoadBalancer("loadBalancerName", rule);

  @Injectable
  Invocation invocation;

  @Mocked
  ServiceRegistry serviceRegistry;

  @Mocked
  InstanceCacheManager instanceCacheManager;

  @Mocked
  TransportManager transportManager;

  @Mocked
  Transport restTransport;

  Response sendResponse;

  List<String> results = new ArrayList<>();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @AfterClass
  public static void classTeardown() {
    ArchaiusUtils.resetConfig();
  }

  @BeforeClass
  public static void beforeCls() {
    ConfigUtil.installDynamicConfig();
    AbstractConfiguration configuration =
        (AbstractConfiguration) DynamicPropertyFactory.getBackingConfigurationSource();
    configuration.addProperty("cse.loadbalance.test.transactionControl.policy",
        "io.servicecomb.loadbalance.filter.SimpleTransactionControlFilter");
    configuration.addProperty("cse.loadbalance.test.transactionControl.options.tag0", "value0");
    configuration.addProperty("cse.loadbalance.test.isolation.enabled", "true");
    configuration.addProperty("cse.loadbalance.serverListFilters", "a");
    configuration.addProperty("cse.loadbalance.serverListFilter.a.className",
        "io.servicecomb.loadbalance.MyServerListFilterExt");
  }

  @Before
  public void setUp() {
    new MockUp<Invocation>(invocation) {
      @Mock
      String getMicroserviceName() {
        return microserviceName;
      }

      @Mock
      void next(AsyncResponse asyncResp) throws Exception {
        asyncResp.handle(sendResponse);
      }
    };

    CseContext.getInstance().setTransportManager(transportManager);
    new MockUp<TransportManager>(transportManager) {
      @Mock
      Transport findTransport(String transportName) {
        return restTransport;
      }
    };

    RegistryUtils.setServiceRegistry(serviceRegistry);
    new MockUp<ServiceRegistry>(serviceRegistry) {
      @Mock
      InstanceCacheManager getInstanceCacheManager() {
        return instanceCacheManager;
      }
    };

    BeansHolder holder = new BeansHolder();
    List<ExtensionsFactory> extensionsFactories = new ArrayList<>();
    extensionsFactories.add(new RuleClassNameExtentionsFactory());
    extensionsFactories.add(new RuleNameExtentionsFactory());
    extensionsFactories.add(new DefaultRetryExtensionsFactory());
    Deencapsulation.setField(holder, "extentionsFactories", extensionsFactories);
    holder.init();
  }

  @After
  public void teardown() {
    CseContext.getInstance().setTransportManager(null);
    RegistryUtils.setServiceRegistry(null);
  }

  @Test
  public void handleClearLoadBalancer() throws Exception {
    new MockUp<LoadbalanceHandler>(handler) {
      @Mock
      LoadBalancer getOrCreateLoadBalancer(Invocation invocation) {
        return loadBalancer;
      }

      @Mock
      void send(Invocation invocation, AsyncResponse asyncResp, final LoadBalancer choosenLB) throws Exception {
      }
    };

    loadBalancerMap.put("old", loadBalancer);
    Deencapsulation.setField(handler, "policy", "init");
    handler.handle(invocation, ar -> {
    });

    Assert.assertThat(loadBalancerMap.values(), Matchers.empty());
  }

  @Test
  public void handleSendNotRetry() throws Exception {
    new MockUp<LoadbalanceHandler>(handler) {
      @Mock
      LoadBalancer getOrCreateLoadBalancer(Invocation invocation) {
        return loadBalancer;
      }

      @Mock
      void send(Invocation invocation, AsyncResponse asyncResp, final LoadBalancer choosenLB) throws Exception {
        results.add("sendNotRetry");
      }
    };

    handler.handle(invocation, ar -> {
    });

    Assert.assertThat(results, Matchers.contains("sendNotRetry"));
  }

  @Test
  public void handleSendWithRetry() throws Exception {
    new MockUp<LoadbalanceHandler>(handler) {
      @Mock
      LoadBalancer getOrCreateLoadBalancer(Invocation invocation) {
        return loadBalancer;
      }

      @Mock
      void sendWithRetry(Invocation invocation, AsyncResponse asyncResp, final LoadBalancer choosenLB)
          throws Exception {
        results.add("sendWithRetry");
      }
    };

    new MockUp<Configuration>(Configuration.INSTANCE) {
      @Mock
      boolean isRetryEnabled(String microservice) {
        return true;
      }
    };

    handler.handle(invocation, ar -> {
    });

    Assert.assertThat(results, Matchers.contains("sendWithRetry"));
  }

  @Test
  public void testSetIsolationFilter() {
    Invocation invocation = Mockito.mock(Invocation.class);
    Mockito.when(invocation.getMicroserviceName()).thenReturn("test");
    LoadbalanceHandler lbHandler = new LoadbalanceHandler();
    LoadBalancer myLB = new LoadBalancer("loadBalancerName", rule);
    lbHandler.setIsolationFilter(myLB, "abc");
    Assert.assertEquals(1, myLB.getFilterSize());

    Mockito.when(invocation.getMicroserviceName()).thenReturn("abc");
    myLB = new LoadBalancer("loadBalancerName", rule);
    lbHandler.setIsolationFilter(myLB, "abc");
    myLB.setInvocation(invocation);

    Assert.assertEquals(1, myLB.getFilterSize());
    Map<String, ServerListFilterExt> filters = Deencapsulation.getField(myLB, "filters");
    List<Server> servers = new ArrayList<>();
    servers.add(new Server(null));
    Assert.assertEquals(servers.size(),
        filters.get("io.servicecomb.loadbalance.filter.IsolationServerListFilter")
            .getFilteredListOfServers(servers)
            .size());
  }

  @Test
  public void setTransactionControlFilter_NoPolicy() {
    new MockUp<Configuration>(Configuration.INSTANCE) {
      @Mock
      String getFlowsplitFilterPolicy(String microservice) {
        return "";
      }
    };

    handler.setTransactionControlFilter(loadBalancer, microserviceName);
    Assert.assertEquals(0, loadBalancer.getFilterSize());
  }

  @Test
  public void setTransactionControlFilter_InvalidPolicy() {
    new MockUp<Configuration>(Configuration.INSTANCE) {
      @Mock
      String getFlowsplitFilterPolicy(String microservice) {
        return "InvalidPolicy";
      }
    };

    expectedException.expect(Error.class);
    expectedException.expectMessage(Matchers.is("Fail to create instance of class: InvalidPolicy"));

    handler.setTransactionControlFilter(loadBalancer, microserviceName);
    Assert.assertEquals(0, loadBalancer.getFilterSize());
  }

  @Test
  public void setTransactionControlFilter_PolicyNotAssignable() {
    new MockUp<Configuration>(Configuration.INSTANCE) {
      @Mock
      String getFlowsplitFilterPolicy(String microservice) {
        return String.class.getName();
      }
    };

    expectedException.expect(Error.class);
    expectedException.expectMessage(Matchers.is("Fail to create instance of class: java.lang.String"));

    handler.setTransactionControlFilter(loadBalancer, microserviceName);
    Assert.assertEquals(0, loadBalancer.getFilterSize());
  }

  @Test
  public void setTransactionControlFilter_Normal() {
    new MockUp<Configuration>(Configuration.INSTANCE) {
      @Mock
      String getFlowsplitFilterPolicy(String microservice) {
        return SimpleTransactionControlFilter.class.getName();
      }
    };

    handler.setTransactionControlFilter(loadBalancer, microserviceName);
    Assert.assertEquals(1, loadBalancer.getFilterSize());
  }

  @Test
  public void getOrCreateLoadBalancer() throws Exception {
    MicroserviceInstance instance = new MicroserviceInstance();
    instance.setInstanceId("id");
    instance.getEndpoints().add("rest://localhost:8080");

    Map<String, MicroserviceInstance> instanceMap = new HashMap<>();
    instanceMap.put(instance.getInstanceId(), instance);

    VersionedCache instanceVersionedCache =
        new VersionedCache().autoCacheVersion().name("instanceCache").data(instanceMap);

    new Expectations() {
      {
        invocation.getConfigTransportName();
        result = "rest";
        instanceCacheManager.getOrCreateVersionedCache(anyString, anyString, anyString);
        result = instanceVersionedCache;
      }
    };

    LoadBalancer lb = handler.getOrCreateLoadBalancer(invocation);

    Assert.assertEquals(2, lb.getFilterSize());
    Assert.assertEquals("instanceCache/rest", lb.getName());
    Assert.assertEquals("[rest://localhost:8080]", Deencapsulation.getField(lb, "serverList").toString());
  }

  @Test
  public void send_noEndPoint() {
    new Expectations(loadBalancer) {
      {
        loadBalancer.chooseServer(invocation);
        result = null;
      }
    };

    Holder<Throwable> result = new Holder<>();
    Deencapsulation.invoke(handler, "send", invocation, (AsyncResponse) resp -> {
      result.value = (Throwable) resp.getResult();
    }, loadBalancer);

    Assert.assertEquals("InvocationException: code=490;msg=CommonExceptionData [message=Cse Internal Bad Request]",
        result.value.getMessage());
    Assert.assertEquals("No available address found. microserviceName=ms, version=null, discoveryGroupName=null",
        result.value.getCause().getMessage());
  }

  @Test
  public void send_failed() {
    CacheEndpoint cacheEndpoint = new CacheEndpoint("rest://localhost:8080", null);
    CseServer server = new CseServer(restTransport, cacheEndpoint);
    new MockUp<System>() {
      @Mock
      long currentTimeMillis() {
        return 123;
      }
    };
    new Expectations(loadBalancer) {
      {
        loadBalancer.chooseServer(invocation);
        result = server;
      }
    };
    int continuousFailureCount = server.getCountinuousFailureCount();

    sendResponse = Response.create(Status.BAD_REQUEST, "send failed");

    Holder<Throwable> result = new Holder<>();
    Deencapsulation.invoke(handler, "send", invocation, (AsyncResponse) resp -> {
      result.value = (Throwable) resp.getResult();
    }, loadBalancer);

    Assert.assertEquals(123, server.getLastVisitTime());
    Assert.assertEquals(1,
        loadBalancer.getLoadBalancerStats().getSingleServerStat(server).getSuccessiveConnectionFailureCount());
    Assert.assertEquals("InvocationException: code=400;msg=send failed",
        result.value.getMessage());
    Assert.assertEquals(continuousFailureCount + 1, server.getCountinuousFailureCount());
  }

  @Test
  public void send_success() {
    CacheEndpoint cacheEndpoint = new CacheEndpoint("rest://localhost:8080", null);
    CseServer server = new CseServer(restTransport, cacheEndpoint);
    new MockUp<System>() {
      @Mock
      long currentTimeMillis() {
        return 123;
      }
    };
    new Expectations(loadBalancer) {
      {
        loadBalancer.chooseServer(invocation);
        result = server;
      }
    };
    server.incrementContinuousFailureCount();

    sendResponse = Response.ok("success");

    Holder<String> result = new Holder<>();
    Deencapsulation.invoke(handler, "send", invocation, (AsyncResponse) resp -> {
      result.value = resp.getResult();
    }, loadBalancer);

    Assert.assertEquals(123, server.getLastVisitTime());
    Assert.assertEquals(1,
        loadBalancer.getLoadBalancerStats().getSingleServerStat(server).getActiveRequestsCount());
    Assert.assertEquals("success", result.value);
    Assert.assertEquals(0, server.getCountinuousFailureCount());
  }

  @Test
  public void sendWithRetry() {
    Holder<String> result = new Holder<>();
    Deencapsulation.invoke(handler, "sendWithRetry", invocation, (AsyncResponse) resp -> {
      result.value = resp.getResult();
    }, loadBalancer);

    // no exception
  }
}
