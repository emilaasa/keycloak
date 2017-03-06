/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.protocol.oidc.mappers;

import org.jboss.logging.Logger;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.representations.IDToken;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.List;

/**
 * OIDC {@link org.keycloak.protocol.ProtocolMapper} that uses a provided JavaScript fragment to compute the token claim value.
 *
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public class ScriptBasedOIDCProtocolMapper extends AbstractOIDCProtocolMapper implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

  public static final String PROVIDER_ID = "oidc-script-based-protocol-mapper";

  private static final Logger LOGGER = Logger.getLogger(ScriptBasedOIDCProtocolMapper.class);

  private static final String SCRIPT = "script";

  private static final List<ProviderConfigProperty> configProperties;

  static {

    configProperties = ProviderConfigurationBuilder.create()
      .property()
      .name(SCRIPT)
      .type(ProviderConfigProperty.SCRIPT_TYPE)
      .label("Script")
      .helpText(
        "Script to compute the claim value. \n" + //
          " Available variables: \n" + //
          " 'user' - the current user.\n" + //
          " 'realm' - the current realm.\n" + //
          " 'token' - the current token.\n" + //
          " 'userSession' - the current userSession.\n" //
      )
      .defaultValue("/**\n" + //
        " * Available variables: \n" + //
        " * user - the current user\n" + //
        " * realm - the current realm\n" + //
        " * token - the current token\n" + //
        " * userSession - the current userSession\n" + //
        " */\n\n\n//insert your code here..." //
      )
      .add()
      .build();

    OIDCAttributeMapperHelper.addAttributeConfig(configProperties, UserPropertyMapper.class);
  }

  public List<ProviderConfigProperty> getConfigProperties() {
    return configProperties;
  }

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public String getDisplayType() {
    return "Script Mapper";
  }

  @Override
  public String getDisplayCategory() {
    return TOKEN_MAPPER_CATEGORY;
  }

  @Override
  public String getHelpText() {
    return "Evaluates a javascript function to produce a token claim based on context information.";
  }

  protected void setClaim(IDToken token, ProtocolMapperModel mappingModel, UserSessionModel userSession) {

    UserModel user = userSession.getUser();
    String script = mappingModel.getConfig().get(SCRIPT);
    RealmModel realm = userSession.getRealm();

    ScriptEngineManager engineManager = new ScriptEngineManager();
    ScriptEngine scriptEngine = engineManager.getEngineByName("javascript");

    Bindings bindings = scriptEngine.createBindings();
    bindings.put("user", user);
    bindings.put("realm", realm);
    bindings.put("token", token);
    bindings.put("userSession", userSession);

    Object claimValue;
    try {
      claimValue = scriptEngine.eval(script, bindings);
    } catch (Exception ex) {
      LOGGER.error("Error during execution of ProtocolMapper script", ex);
      claimValue = null;
    }

    OIDCAttributeMapperHelper.mapClaim(token, mappingModel, claimValue);
  }

  public static ProtocolMapperModel createClaimMapper(String name,
                                                      String userAttribute,
                                                      String tokenClaimName, String claimType,
                                                      boolean consentRequired, String consentText,
                                                      boolean accessToken, boolean idToken) {
    return OIDCAttributeMapperHelper.createClaimMapper(name, userAttribute,
      tokenClaimName, claimType,
      consentRequired, consentText,
      accessToken, idToken,
      PROVIDER_ID);
  }
}
