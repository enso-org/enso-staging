package org.enso.languageserver.websocket.json

import io.circe.literal._
import io.circe.{Json, JsonObject}
import nl.gn0s1s.bump.SemVer
import org.enso.editions.{Editions, LibraryName}
import org.enso.languageserver.libraries.LibraryEntry
import org.enso.languageserver.libraries.LibraryEntry.PublishedLibraryVersion
import org.enso.librarymanager.published.bundles.LocalReadOnlyRepository
import org.enso.librarymanager.published.repository.{
  EmptyRepository,
  ExampleRepository
}
import org.enso.pkg.{Contact, PackageManager}

import java.nio.file.Files

class LibrariesTest extends BaseServerTest {
  private val libraryRepositoryPort: Int = 47308

  private val exampleRepo   = new ExampleRepository
  private val baseUrl       = s"http://localhost:$libraryRepositoryPort/"
  private val repositoryUrl = baseUrl + "libraries"

  override protected def customEdition: Option[Editions.RawEdition] = Some(
    exampleRepo.createEdition(repositoryUrl)
  )

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
              "namespace": "user",
              "name": "My_Local_Lib",
              "authors": [
                {
                  "name": "user",
                  "email": "example@example.com"
                }
              ],
              "maintainers": [
                {
                  "name": "only-name"
                },
                {
                  "email": "foo@example.com"
                }
              ],
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
                  "namespace": "user",
                  "name": "My_Local_Lib",
                  "version": {
                    "type": "LocalLibraryVersion"
                  },
                  "isCached": true
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

    "validate the library name" ignore {
      // TODO [RW] error handling (#1877)
    }

    "get and set the metadata" in {
      val client = getInitialisedWsClient()
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "library/create",
            "id": 0,
            "params": {
              "namespace": "user",
              "name": "Metadata_Test_Lib",
              "authors": [],
              "maintainers": [],
              "license": ""
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 0,
            "result": null
          }
          """)

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "library/getMetadata",
            "id": 1,
            "params": {
              "namespace": "user",
              "name": "Metadata_Test_Lib",
              "version": {
                "type": "LocalLibraryVersion"
              }
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "result": {
              "description": null,
              "tagLine": null
            }
          }
          """)

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "library/setMetadata",
            "id": 2,
            "params": {
              "namespace": "user",
              "name": "Metadata_Test_Lib",
              "description": "The description...",
              "tagLine": "tag-line"
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 2,
            "result": null
          }
          """)

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "library/getMetadata",
            "id": 3,
            "params": {
              "namespace": "user",
              "name": "Metadata_Test_Lib",
              "version": {
                "type": "LocalLibraryVersion"
              }
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 3,
            "result": {
              "description": "The description...",
              "tagLine": "tag-line"
            }
          }
          """)
    }

    "create, publish a library and fetch its manifest from the server" in {
      val client = getInitialisedWsClient()
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "library/create",
            "id": 0,
            "params": {
              "namespace": "user",
              "name": "Publishable_Lib",
              "authors": [
                {
                  "name": "user",
                  "email": "example@example.com"
                }
              ],
              "maintainers": [
                {
                  "name": "only-name"
                },
                {
                  "email": "foo@example.com"
                }
              ],
              "license": ""
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 0,
            "result": null
          }
          """)

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "library/setMetadata",
            "id": 1,
            "params": {
              "namespace": "user",
              "name": "Publishable_Lib",
              "description": "Description for publication.",
              "tagLine": "published-lib"
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "result": null
          }
          """)

      val repoRoot = getTestDirectory.resolve("libraries_repo_root")
      EmptyRepository.withServer(
        libraryRepositoryPort,
        repoRoot,
        uploads = true
      ) {
        val uploadUrl       = baseUrl + "upload"
        val uploadRequestId = 2
        client.send(json"""
          { "jsonrpc": "2.0",
            "method": "library/publish",
            "id": $uploadRequestId,
            "params": {
              "namespace": "user",
              "name": "Publishable_Lib",
              "authToken": "SOME TOKEN",
              "uploadUrl": $uploadUrl,
              "bumpVersionAfterPublish": null
            }
          }
          """)

        var found = false
        while (!found) {
          val rawResponse = client.expectSomeJson()
          val response    = rawResponse.asObject.value
          val idMatches = response("id")
            .flatMap(_.asNumber)
            .flatMap(_.toInt)
            .contains(uploadRequestId)
          if (idMatches) {
            rawResponse shouldEqual json"""
              { "jsonrpc": "2.0",
                "id": $uploadRequestId,
                "result": null
              }
              """

            found = true
          }
        }

        val libraryRoot = repoRoot
          .resolve("libraries")
          .resolve("user")
          .resolve("Publishable_Lib")
          .resolve("0.0.1")
        val mainPackage = libraryRoot.resolve("main.tgz")
        assert(Files.exists(mainPackage))
        val pkg = PackageManager.Default.loadPackage(libraryRoot.toFile).get
        pkg.config.authors should contain theSameElementsAs Seq(
          Contact(name = Some("user"), email = Some("example@example.com"))
        )
        pkg.config.maintainers should contain theSameElementsAs Seq(
          Contact(name = Some("only-name"), email = None),
          Contact(name = None, email              = Some("foo@example.com"))
        )

        client.send(json"""
          { "jsonrpc": "2.0",
            "method": "library/getMetadata",
            "id": 3,
            "params": {
              "namespace": "user",
              "name": "Publishable_Lib",
              "version": {
                "type": "PublishedLibraryVersion",
                "version": "0.0.1",
                "repositoryUrl": $repositoryUrl
              }
            }
          }
          """)
        client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 3,
            "result": {
              "description": "Description for publication.",
              "tagLine": "published-lib"
            }
          }
          """)
      }

      // Once the server is down, the metadata request should fail, especially as the published version is not cached.
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "library/getMetadata",
            "id": 4,
            "params": {
              "namespace": "user",
              "name": "Publishable_Lib",
              "version": {
                "type": "PublishedLibraryVersion",
                "version": "0.0.1",
                "repositoryUrl": $repositoryUrl
              }
            }
          }
          """)
      val errorMsg = client.expectSomeJson().asObject.value
      errorMsg("id").value.asNumber.value.toInt.value shouldEqual 4
      errorMsg("error").value.asObject shouldBe defined
    }
  }

  "library/preinstall" should {
    "download the library sending progress notifications " +
    "and correctly place it in cache" in {
      val client = getInitialisedWsClient()

      val repositoryPath = getTestDirectory.resolve("repository_path")
      exampleRepo.createRepository(repositoryPath)
      exampleRepo.withServer(libraryRepositoryPort, repositoryPath) {
        val requestId = 0
        client.send(json"""
          { "jsonrpc": "2.0",
            "method": "library/preinstall",
            "id": $requestId,
            "params": {
              "namespace": "Foo",
              "name": "Bar"
            }
          }
          """)

        val messages         = collection.mutable.ListBuffer[(String, JsonObject)]()
        var waitingForTask   = true
        var waitingForResult = true

        while (waitingForTask || waitingForResult) {
          val msg = client.expectSomeJson().asObject.value

          msg("id") match {
            case Some(json) =>
              json.asNumber.value.toInt.value shouldEqual requestId
              msg("result").value.asNull.value
              waitingForResult = false
            case None =>
              val method =
                msg("method").map(_.asString.value).getOrElse("error")
              val params =
                msg("params").map(_.asObject.value).getOrElse(JsonObject())
              messages.addOne((method, params))

              if (method == "task/finished") waitingForTask = false
          }
        }

        val taskStart = messages.find(_._1 == "task/started").value
        val taskId    = taskStart._2("taskId").value.asString.value
        taskStart
          ._2("relatedOperation")
          .value
          .asString
          .value shouldEqual "library/preinstall"

        val updates = messages.filter { case (method, params) =>
          method == "task/progress-update" &&
          params("taskId").value.asString.value == taskId
        }

        updates should not be empty
        updates.head._2("message").value.asString.value should include(
          "Installing"
        )

        val cachePath     = getTestDirectory.resolve("test_data").resolve("lib")
        val readOnlyCache = new LocalReadOnlyRepository(cachePath)
        val cachedLibraryRoot = readOnlyCache
          .findCachedLibrary(
            LibraryName("Foo", "Bar"),
            SemVer(1, 0, 0)
          )
          .value

        val pkg =
          PackageManager.Default.loadPackage(cachedLibraryRoot.toFile).get
        pkg.name shouldEqual "Bar"
        pkg.listSources.map(
          _.file.getName
        ) should contain theSameElementsAs Seq("Main.enso")
      }
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
      val repo     = new ExampleRepository
      val repoPath = getTestDirectory.resolve("repo_root")
      repo.createRepository(repoPath)
      repo.withServer(43707, repoPath) {
        val client = getInitialisedWsClient()

        client.send(json"""
          { "jsonrpc": "2.0",
            "method": "editions/listAvailable",
            "id": 0,
            "params": {
              "update": true
            }
          }
          """)
        client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 0,
            "result": {
              "editionNames": [
                ${buildinfo.Info.currentEdition},
                "testlocal"
              ]
            }
          }
          """)
      }
    }
  }

  case class PublishedLibrary(
    namespace: String,
    name: String,
    isCached: Boolean
  )

  def extractPublishedLibraries(response: Json): Seq[PublishedLibrary] = {
    val result = response.asObject.value("result").value
    val libs   = result.asObject.value("availableLibraries").value
    val parsed = libs.asArray.value.map(_.as[LibraryEntry])
    parsed.collect {
      case Right(
            LibraryEntry(
              namespace,
              name,
              PublishedLibraryVersion(_, _),
              isCached
            )
          ) =>
        PublishedLibrary(namespace, name, isCached)
    }
  }

  "editions/listDefinedLibraries" should {
    "include expected libraries in the list" in {
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
      val published = extractPublishedLibraries(client.expectSomeJson())

      published should contain(
        PublishedLibrary("Standard", "Base", isCached = true)
      )
      published should contain(
        PublishedLibrary("Foo", "Bar", isCached = false)
      )

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
      extractPublishedLibraries(client.expectSomeJson()) should contain(
        PublishedLibrary("Standard", "Base", isCached = true)
      )
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
