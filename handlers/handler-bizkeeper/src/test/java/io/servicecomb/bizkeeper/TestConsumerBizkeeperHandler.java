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

package io.servicecomb.bizkeeper;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.hystrix.strategy.HystrixPlugins;

import io.servicecomb.core.Invocation;
import io.servicecomb.core.definition.OperationMeta;

/**
 *
 *
 */
public class TestConsumerBizkeeperHandler {

  @Test
  public void testCreateBizkeeperCommand() {
    HystrixPlugins.reset();
    ConsumerBizkeeperHandler consumerBizkeeperHandler = new ConsumerBizkeeperHandler();

    Invocation invocation = Mockito.mock(Invocation.class);
    Mockito.when(invocation.getOperationMeta()).thenReturn(Mockito.mock(OperationMeta.class));
    Mockito.when(invocation.getOperationMeta().getMicroserviceQualifiedName()).thenReturn("test1");

    CommandKey.toHystrixCommandGroupKey("groupname", invocation);
    CommandKey.toHystrixCommandKey("groupname", invocation);
    BizkeeperCommand command = consumerBizkeeperHandler.createBizkeeperCommand(invocation);
    Assert.assertNotNull(command);
  }
}
