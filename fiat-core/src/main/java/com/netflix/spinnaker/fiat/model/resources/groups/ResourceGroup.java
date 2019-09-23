/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.model.resources.groups;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import lombok.Data;

/**
 * A ResourceGroup provides additional rule-based permissions to resources.
 *
 * <p>A ResourceGroup allows for encoding of permissions for an AccessControlled resource where
 * those permissions can be derived from that resource rather than explicitly encoded as properties
 * on the resource.
 *
 * <p>As a concrete example, {@see PrefixResourceGroup} which allows for defining common permissions
 * on resources that start with a common prefix.
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "resourceGroupType")
@JsonSubTypes(@JsonSubTypes.Type(value = PrefixResourceGroup.class, name = "PREFIX"))
public abstract class ResourceGroup {
  /**
   * Whether the supplied resource matches this ResourceGroup.
   *
   * @param resource the resource to check
   * @return true if the permissions on this ResourceGroup should be applied to the resource.
   */
  public abstract boolean contains(Resource.AccessControlled resource);

  /** The type of resource this ResourceGroup applies to. */
  private ResourceType resourceType;

  /** The concrete type of this ResourceGroup. */
  // needed for serialization
  private ResourceGroupType resourceGroupType;

  /** The Permissions that should get added to resources that match this ResourceGroup. */
  private Permissions permissions = Permissions.EMPTY;
}
