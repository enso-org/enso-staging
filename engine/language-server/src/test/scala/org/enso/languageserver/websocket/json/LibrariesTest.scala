package org.enso.languageserver.websocket.json

import io.circe.literal._
import io.circe.{Json, JsonObject}
import org.enso.languageserver.libraries.LibraryEntry
import org.enso.languageserver.libraries.LibraryEntry.PublishedLibraryVersion

class LibrariesTest extends BaseServerTest {
  "LocalLibraryManager" should {
    "create a library project and include it on the list of local projects" in {
      val client = getInitialisedWsClient()
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "library/listLocal",
            "id": 0
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 0,
            "result": {
              "localLibraries": []
            }
          }
          """)

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "library/create",
            "id": 1,
            "params": {
              "namespace": "User",
              "name": "MyLocalLib",
              "authors": [],
              "maintainers": [],
              "license": ""
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "result": null
          }
          """)

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "library/listLocal",
            "id": 2
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 2,
            "result": {
              "localLibraries": [
                {
                  "namespace": "User",
                  "name": "MyLocalLib",
                  "version": {
                    "type": "LocalLibraryVersion"
                  }
                }
              ]
            }
          }
          """)
    }

    "fail with LibraryAlreadyExists when creating a library that already " +
    "existed" ignore {
      // TODO [RW] error handling (#1877)
    }
  }

  "mocked library/preinstall" should {
    "send progress notifications" in {
      val client = getInitialisedWsClient()
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "library/preinstall",
            "id": 0,
            "params": {
              "namespace": "Foo",
              "name": "Test"
            }
          }
          """)
      val messages =
        for (_ <- 0 to 3) yield {
          val msg    = client.expectSomeJson().asObject.value
          val method = msg("method").map(_.asString.value).getOrElse("error")
          val params =
            msg("params").map(_.asObject.value).getOrElse(JsonObject())
          (method, params)
        }

      val taskStart = messages.find(_._1 == "task/started").value
      val taskId    = taskStart._2("taskId").value.asString.value
      taskStart
        ._2("relatedOperation")
        .value
        .asString
        .value shouldEqual "library/preinstall"

      taskStart._2("unit").value.asString.value shouldEqual "Bytes"

      val updates = messages.filter { case (method, params) =>
        method == "task/progress-update" &&
        params("taskId").value.asString.value == taskId
      }

      updates should not be empty
      updates.head
        ._2("message")
        .value
        .asString
        .value shouldEqual "Download Test"
    }
  }

  "editions/listAvailable" should {
    "list editions on the search path" in {
      val client = getInitialisedWsClient()
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "editions/listAvailable",
            "id": 0,
            "params": {
              "update": false
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 0,
            "result": {
              "editionNames": [
                ${buildinfo.Info.currentEdition}
              ]
            }
          }
          """)
    }

    "update the list of editions if requested" ignore {
      // TODO [RW] updating editions
    }
  }

  "editions/listDefinedLibraries" should {
    "include Standard.Base in the list" in {
      def containsBase(response: Json): Unit = {
        val result = response.asObject.value("result").value
        val libs   = result.asObject.value("availableLibraries").value
        val parsed = libs.asArray.value.map(_.as[LibraryEntry])
        val bases = parsed.collect {
          case Right(
                LibraryEntry("Standard", "Base", PublishedLibraryVersion(_, _))
              ) =>
            ()
        }
        bases should have size 1
      }

      val client = getInitialisedWsClient()
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "editions/listDefinedLibraries",
            "id": 0,
            "params": {
              "edition": {
                "type": "CurrentProjectEdition"
              }
            }
          }
          """)
      containsBase(client.expectSomeJson())

      val currentEditionName = buildinfo.Info.currentEdition
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "editions/listDefinedLibraries",
            "id": 0,
            "params": {
              "edition": {
                "type": "NamedEdition",
                "editionName": $currentEditionName
              }
            }
          }
          """)
      containsBase(client.expectSomeJson())
    }
  }

  "editions/resolve" should {
    "resolve the engine version associated with an edition" in {
      val currentVersion = buildinfo.Info.ensoVersion

      val client = getInitialisedWsClient()
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "editions/resolve",
            "id": 0,
            "params": {
              "edition": {
                "type": "CurrentProjectEdition"
              }
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 0,
            "result": {
              "engineVersion": $currentVersion
            }
          }
          """)

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "editions/resolve",
            "id": 1,
            "params": {
              "edition": {
                "type": "NamedEdition",
                "editionName": ${buildinfo.Info.currentEdition}
              }
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "result": {
              "engineVersion": $currentVersion
            }
          }
          """)
    }
  }
}
