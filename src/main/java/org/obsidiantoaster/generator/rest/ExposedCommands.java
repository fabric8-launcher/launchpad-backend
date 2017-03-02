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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 */
public class ExposedCommands
{
   private final Map<String, String> commandMap = new TreeMap<>();
   private final Map<String, String> queryMap = new TreeMap<>();
   private final Set<String> zipCommands = new HashSet<>();

   public ExposedCommands()
   {
      addZipCommand("obsidian-new-quickstart", "Obsidian: New Quickstart");
      addZipCommand("obsidian-new-project", "Obsidian: New Project");
   }

   /**
    * Returns the commands which are allowed to be exported over the REST API via POST
    */
   public Map<String, String> getAllowedCommands()
   {
      return commandMap;
   }

   /**
    * Returns the queries which are allowed to be exported over the REST API via GET
    */
   public Map<String, String> getAllowedQueries()
   {
      return queryMap;
   }

   /**
    * Returns true if the command should generate a zip file as part of the execution result
    */
   public boolean generateZipCommand(String commandName)
   {
      return zipCommands.contains(commandName);
   }

   /**
    * Returns the label of the given command name or null if it is not supported
    */
   public String getCommandLabel(String name)
   {
      return commandMap.get(name);
   }

   /**
    * Validates the command name is valid
    */
   public void validateCommand(String name) {
      if (commandMap.get(name) == null) {
         String message;
         if (commandMap.isEmpty()) {
            message = "No commands are supported by this service";
         } else
         {
            message = "No such command `" + name + "`. Supported commmands are '" + String
                     .join("', '", commandMap.keySet()) + "'";
         }
         throw new WebApplicationException(message, Response.Status.NOT_FOUND);
      }
   }


   /**
    * Validates the query name is valid
    */
   public void validateQuery(String name) {
      if (queryMap.get(name) == null) {
         String message;
         if (queryMap.isEmpty()) {
            message = "No queries are supported by this service";
         } else
         {
            message = "No such query `" + name + "`. Supported queries are '" + String
                     .join("', '", queryMap.keySet()) + "'";
         }
         throw new WebApplicationException(message, Response.Status.NOT_FOUND);
      }
   }


   protected void addQuery(String name, String label)
   {
      queryMap.put(name, label);
      addCommand(name, label);
   }

   protected void addCommand(String name, String label)
   {
      commandMap.put(name, label);
   }

   protected void addZipCommand(String name, String label)
   {
      addCommand(name, label);
      zipCommands.add(name);
   }
}
