/**
 *  Copyright 2005-2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.obsidiantoaster.generator.rest;

import org.jboss.forge.addon.resource.Resource;
import org.jboss.forge.addon.resource.ResourceFactory;
import org.jboss.forge.addon.ui.command.CommandFactory;
import org.jboss.forge.addon.ui.command.UICommand;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UISelection;
import org.jboss.forge.addon.ui.controller.CommandController;
import org.jboss.forge.addon.ui.controller.CommandControllerFactory;
import org.jboss.forge.addon.ui.controller.WizardCommandController;
import org.jboss.forge.addon.ui.result.CompositeResult;
import org.jboss.forge.addon.ui.result.Failed;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.furnace.versions.Versions;
import org.jboss.forge.service.ui.RestUIContext;
import org.jboss.forge.service.ui.RestUIRuntime;
import org.jboss.forge.service.util.UICommandHelper;
import org.obsidiantoaster.generator.ForgeInitializer;
import org.obsidiantoaster.generator.event.FurnaceStartup;
import org.obsidiantoaster.generator.util.JsonBuilder;

import javax.enterprise.concurrent.ManagedExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.json.Json.createObjectBuilder;

@javax.ws.rs.Path("/forge")
@ApplicationScoped
public class ObsidianResource
{
   private static final String DEFAULT_COMMAND_NAME = "obsidian-new-quickstart";

   private static final Logger log = Logger.getLogger(ObsidianResource.class.getName());

   private final ExposedCommands exposedCommands;

   private final BlockingQueue<Path> directoriesToDelete = new LinkedBlockingQueue<>();

   @javax.annotation.Resource
   private ManagedExecutorService executorService;

   public ObsidianResource()
   {
      exposedCommands = new ExposedCommands();
   }

   @Inject
   private CommandFactory commandFactory;

   @Inject
   private CommandControllerFactory controllerFactory;

   @Inject
   private ResourceFactory resourceFactory;

   @Inject
   private UICommandHelper helper;

   void init(@Observes FurnaceStartup startup)
   {
      try
      {
         log.info("Warming up internal cache");
         // Warm up
         getCommand(DEFAULT_COMMAND_NAME, null);
         log.info("Caches warmed up");
         executorService.submit(() -> {
            java.nio.file.Path path = null;
            try
            {
               while ((path = directoriesToDelete.take()) != null)
               {
                  log.info("Deleting " + path);
                  org.obsidiantoaster.generator.util.Paths.deleteDirectory(path);
               }
            }
            catch (IOException io)
            {
               log.log(Level.SEVERE, "Error while deleting" + path, io);
            }
            catch (InterruptedException e)
            {
               // Do nothing
            }
         });
      }
      catch (Exception e)
      {
         log.log(Level.SEVERE, "Error while warming up cache", e);
      }
   }

   @GET
   @javax.ws.rs.Path("/version")
   @Produces(MediaType.APPLICATION_JSON)
   public JsonObject getInfo()
   {
      return createObjectBuilder()
               .add("backendVersion", String.valueOf(ForgeInitializer.getVersion()))
               .add("forgeVersion", Versions.getImplementationVersionFor(UIContext.class).toString())
               .build();
   }

   @GET
   @javax.ws.rs.Path("/commands/{commandName}")
   @Produces(MediaType.APPLICATION_JSON)
   public JsonObject getCommandInfo(
            @PathParam("commandName") @DefaultValue(DEFAULT_COMMAND_NAME) String commandName,
            @Context HttpHeaders headers)
            throws Exception
   {
      exposedCommands.validateCommand(commandName);
      JsonObjectBuilder builder = createObjectBuilder();
      try (CommandController controller = getCommand(commandName, headers))
      {
         helper.describeController(builder, controller);
      }
      return builder.build();
   }

   @POST
   @javax.ws.rs.Path("/commands/{commandName}/validate")
   @Consumes(MediaType.APPLICATION_JSON)
   @Produces(MediaType.APPLICATION_JSON)
   public JsonObject validateCommand(JsonObject content,
            @PathParam("commandName") @DefaultValue(DEFAULT_COMMAND_NAME) String commandName,
            @Context HttpHeaders headers)
            throws Exception
   {
      exposedCommands.validateCommand(commandName);
      JsonObjectBuilder builder = createObjectBuilder();
      try (CommandController controller = getCommand(commandName, headers))
      {
         helper.populateControllerAllInputs(content, controller);
         helper.describeCurrentState(builder, controller);
         helper.describeValidation(builder, controller);
         helper.describeInputs(builder, controller);
      }
      return builder.build();
   }

   @POST
   @javax.ws.rs.Path("/commands/{commandName}/next")
   @Consumes(MediaType.APPLICATION_JSON)
   @Produces(MediaType.APPLICATION_JSON)
   public JsonObject nextStep(JsonObject content,
            @PathParam("commandName") @DefaultValue(DEFAULT_COMMAND_NAME) String commandName,
            @Context HttpHeaders headers)
            throws Exception
   {
      exposedCommands.validateCommand(commandName);
      int stepIndex = content.getInt("stepIndex", 1);
      JsonObjectBuilder builder = createObjectBuilder();
      try (CommandController controller = getCommand(commandName, headers))
      {
         if (!(controller instanceof WizardCommandController))
         {
            throw new WebApplicationException("Controller is not a wizard", Status.BAD_REQUEST);
         }
         WizardCommandController wizardController = (WizardCommandController) controller;
         for (int i = 0; i < stepIndex; i++)
         {
            if (wizardController.canMoveToNextStep())
            {
               helper.populateController(content, wizardController);
               helper.describeValidation(builder, controller);
               wizardController.next().initialize();
            }
         }
         helper.describeMetadata(builder, controller);
         helper.describeCurrentState(builder, controller);
         helper.describeInputs(builder, controller);
      }
      return builder.build();
   }

   @GET
   @javax.ws.rs.Path("/commands/{commandName}/query")
   @Consumes(MediaType.MEDIA_TYPE_WILDCARD)
   @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
   public Response executeQuery(@Context UriInfo uriInfo,
            @PathParam("commandName") String commandName,
            @Context HttpHeaders headers)
            throws Exception
   {
      exposedCommands.validateQuery(commandName);
      String stepIndex = null;
      MultivaluedMap<String, String> parameters = uriInfo.getQueryParameters();
      List<String> stepValues = parameters.get("stepIndex");
      if (stepValues != null && !stepValues.isEmpty())
      {
         stepIndex = stepValues.get(0);
      }
      if (stepIndex == null) {
         stepIndex = "0";
      }
      final JsonBuilder jsonBuilder = new JsonBuilder().createJson(Integer.valueOf(stepIndex));
      for (Map.Entry<String, List<String>> entry : parameters.entrySet())
      {
         String key = entry.getKey();
         if (!"stepIndex".equals(key))
         {
            jsonBuilder.addInput(key, entry.getValue());
         }
      }

      final Response response = executeCommandJson(jsonBuilder.build(), commandName, headers);
      if (response.getEntity() instanceof JsonObject)
      {
         JsonObject responseEntity = (JsonObject) response.getEntity();
         String error = ((JsonObject) responseEntity.getJsonArray("messages").get(0)).getString("description");
         return Response.status(Status.PRECONDITION_FAILED).entity(error).build();
      }
      return response;
   }

   @POST
   @javax.ws.rs.Path("/commands/{commandName}/execute")
   @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
   public Response executeCommand(Form form,
            @PathParam("commandName") @DefaultValue(DEFAULT_COMMAND_NAME) String commandName,
            @Context HttpHeaders headers)
            throws Exception
   {
      exposedCommands.validateCommand(commandName);
      String stepIndex = null;
      List<String> stepValues = form.asMap().remove("stepIndex");
      if (stepValues != null && !stepValues.isEmpty())
      {
         stepIndex = stepValues.get(0);
      }
      if (stepIndex == null) {
         stepIndex = "0";
      }
      final JsonBuilder jsonBuilder = new JsonBuilder().createJson(Integer.valueOf(stepIndex));
      for (Map.Entry<String, List<String>> entry : form.asMap().entrySet())
      {
         jsonBuilder.addInput(entry.getKey(), entry.getValue());
      }

      final Response response = executeCommandJson(jsonBuilder.build(), commandName, headers);
      if (response.getEntity() instanceof JsonObject)
      {
         JsonObject responseEntity = (JsonObject) response.getEntity();
         String error = ((JsonObject) responseEntity.getJsonArray("messages").get(0)).getString("description");
         return Response.status(Status.PRECONDITION_FAILED).entity(error).build();
      }
      return response;
   }

   @POST
   @javax.ws.rs.Path("/commands/{commandName}/zip")
   @Consumes(MediaType.APPLICATION_JSON)
   public Response downloadZip(JsonObject content,
            @PathParam("commandName") @DefaultValue(DEFAULT_COMMAND_NAME) String commandName,
            @Context HttpHeaders headers)
            throws Exception
   {
      exposedCommands.validateZipCommand(commandName);
      try (CommandController controller = getCommand(commandName, headers))
      {
         helper.populateControllerAllInputs(content, controller);
         if (controller.isValid())
         {
            Result result = controller.execute();
            if (result instanceof Failed)
            {
               JsonObjectBuilder builder = Json.createObjectBuilder();
               helper.describeResult(builder,result);
               return Response.status(Status.INTERNAL_SERVER_ERROR).entity(builder).build();
            }
            else
            {
               UISelection<?> selection = controller.getContext().getSelection();
               java.nio.file.Path path = Paths.get(selection.get().toString());
               // Find artifactId
               String artifactId = content.getJsonArray("inputs").stream()
                        .filter(input -> "named".equals(((JsonObject) input).getString("name")))
                        .map(input -> ((JsonString) ((JsonObject) input).get("value")).getString())
                        .findFirst().orElse("demo");
               byte[] zipContents = org.obsidiantoaster.generator.util.Paths.zip(artifactId, path);
               directoriesToDelete.offer(path);
               return Response
                        .ok(zipContents)
                        .type("application/zip")
                        .header("Content-Disposition", "attachment; filename=\"" + artifactId + ".zip\"")
                        .build();
            }
         }
         else
         {
            JsonObjectBuilder builder = createObjectBuilder();
            helper.describeValidation(builder, controller);
            return Response.status(Status.PRECONDITION_FAILED).entity(builder.build()).build();
         }
      }
   }

   @POST
   @javax.ws.rs.Path("/commands/{commandName}/execute")
   @Consumes(MediaType.APPLICATION_JSON)
   public Response executeCommandJson(JsonObject content,
            @PathParam("commandName") @DefaultValue(DEFAULT_COMMAND_NAME) String commandName,
            @Context HttpHeaders headers)
            throws Exception
   {
      exposedCommands.validateCommand(commandName);
      try (CommandController controller = getCommand(commandName, headers))
      {
         helper.populateControllerAllInputs(content, controller);
         if (controller.isValid())
         {
            Result result = controller.execute();
            if (result instanceof Failed)
            {
               JsonObjectBuilder builder = Json.createObjectBuilder();
               helper.describeResult(builder,result);
               return Response.status(Status.INTERNAL_SERVER_ERROR).entity(builder).build();
            }
            else
            {
               Object entity = getEntity(result);
               if (entity != null)
               {
                  return Response
                           .ok(entity)
                           .type(MediaType.APPLICATION_JSON)
                           .build();
               }
               else
               {
                  return Response
                           .ok(getMessage(result))
                           .type(MediaType.TEXT_PLAIN)
                           .build();
               }
            }
         }
         else
         {
            JsonObjectBuilder builder = createObjectBuilder();
            helper.describeValidation(builder, controller);
            return Response.status(Status.PRECONDITION_FAILED).entity(builder.build()).build();
         }
      }
   }

   /**
    * Returns the entity from the result handling {@link CompositeResult} values as a List of entities
    */
   protected static Object getEntity(Result result)
   {
      if (result instanceof CompositeResult) {
         CompositeResult compositeResult = (CompositeResult) result;
         List<Object> answer = new ArrayList<>();
         List<Result> results = compositeResult.getResults();
         for (Result child : results)
         {
            Object entity = getEntity(child);
            answer.add(entity);
         }
         return answer;
      }
      return result.getEntity().get();
   }

   /**
    * Returns the result message handling composite results
    */
   protected static String getMessage(Result result)
   {
      if (result instanceof CompositeResult) {
         CompositeResult compositeResult = (CompositeResult) result;
         StringBuilder builder = new StringBuilder();
         List<Result> results = compositeResult.getResults();
         for (Result child : results)
         {
            String message = getMessage(child);
            if (message != null && message.trim().length() > 0)
            {
               if (builder.length() > 0)
               {
                  builder.append("\n");
               }
               builder.append(message);
            }
         }
         return builder.toString();

      }
      return result.getMessage();
   }

   private CommandController getCommand(String name, HttpHeaders headers) throws Exception
   {
      RestUIContext context = createUIContext(headers);
      UICommand command = commandFactory.getNewCommandByName(context, exposedCommands.getCommandLabel(name));
      CommandController controller = controllerFactory.createController(context,
               new RestUIRuntime(Collections.emptyList()), command);
      controller.initialize();
      return controller;
   }

   private RestUIContext createUIContext(HttpHeaders headers)
   {
      java.nio.file.Path rootPath = ForgeInitializer.getRoot();
      Resource<?> selection = resourceFactory.create(rootPath.toFile());
      RestUIContext context = new RestUIContext(selection, Collections.emptyList());
      if (headers != null)
      {
         Map<Object, Object> attributeMap = context.getAttributeMap();
         MultivaluedMap<String, String> requestHeaders = headers.getRequestHeaders();
         requestHeaders.keySet().forEach(key -> attributeMap.put(key, headers.getRequestHeader(key)));
      }
      return context;
   }
}
