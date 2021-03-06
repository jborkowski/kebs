package pl.iterators.kebs_examples

import java.net.URL
import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import pl.iterators.kebs.json.KebsSpray
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object SprayJsonFormat {

  trait JsonProtocol extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val urlJsonFormat = new JsonFormat[URL] {
      override def read(json: JsValue): URL = json match {
        case JsString(url) => Try(new URL(url)).getOrElse(deserializationError("Invalid URL format"))
        case _             => deserializationError("URL should be string")
      }

      override def write(obj: URL): JsValue = JsString(obj.toString)
    }

    implicit val uuidFormat = new JsonFormat[UUID] {
      override def write(obj: UUID): JsValue = JsString(obj.toString)

      override def read(json: JsValue): UUID = json match {
        case JsString(uuid) => Try(UUID.fromString(uuid)).getOrElse(deserializationError("Expected UUID format"))
        case _              => deserializationError("Expected UUID format")
      }
    }
  }

  trait ThingsService {
    def create(request: ThingCreateRequest): Future[ThingCreateResponse]
  }

  object BeforeKebs {
    object ThingProtocol extends JsonProtocol {
      def jsonFlatFormat[P, T <: Product](construct: P => T)(implicit jw: JsonWriter[P], jr: JsonReader[P]): JsonFormat[T] =
        new JsonFormat[T] {
          override def read(json: JsValue): T = construct(jr.read(json))
          override def write(obj: T): JsValue = jw.write(obj.productElement(0).asInstanceOf[P])
        }

      implicit val errorJsonFormat              = jsonFormat1(Error.apply)
      implicit val thingIdJsonFormat            = jsonFlatFormat(ThingId.apply)
      implicit val tagIdJsonFormat              = jsonFlatFormat(TagId.apply)
      implicit val thingNameJsonFormat          = jsonFlatFormat(ThingName.apply)
      implicit val thingDescriptionJsonFormat   = jsonFlatFormat(ThingDescription.apply)
      implicit val locationJsonFormat           = jsonFormat2(Location.apply)
      implicit val createThingRequestJsonFormat = jsonFormat5(ThingCreateRequest.apply)
      implicit val thingJsonFormat              = jsonFormat6(Thing.apply)
    }

    class ThingRouter(thingsService: ThingsService)(implicit ec: ExecutionContext) {
      import ThingProtocol._
      def createRoute = (post & pathEndOrSingleSlash & entity(as[ThingCreateRequest])) { request =>
        complete {
          thingsService.create(request).map[ToResponseMarshallable] {
            case ThingCreateResponse.Created(thing) => Created  -> thing
            case ThingCreateResponse.AlreadyExists  => Conflict -> Error("Already exists")
          }
        }
      }
    }
  }

  object AfterKebs {
    object ThingProtocol extends JsonProtocol with KebsSpray

    class ThingRouter(thingsService: ThingsService)(implicit ec: ExecutionContext) {
      import ThingProtocol._
      def createRoute = (post & pathEndOrSingleSlash & entity(as[ThingCreateRequest])) { request =>
        complete {
          thingsService.create(request).map[ToResponseMarshallable] {
            case ThingCreateResponse.Created(thing) => Created  -> thing
            case ThingCreateResponse.AlreadyExists  => Conflict -> Error("Already exists")
          }
        }
      }
    }
  }

  case class ThingId(uuid: UUID)
  case class ThingName(name: String)
  case class ThingDescription(description: String)
  case class TagId(id: String)
  case class Location(latitude: Double, longitude: Double)

  case class Thing(id: ThingId, name: ThingName, description: ThingDescription, pictureUrl: URL, tags: List[TagId], location: Location)

  case class ThingCreateRequest(name: ThingName,
                                description: ThingDescription,
                                pictureUrl: Option[URL],
                                tags: List[TagId],
                                location: Location)
  sealed abstract class ThingCreateResponse
  object ThingCreateResponse {
    case class Created(thing: Thing) extends ThingCreateResponse
    case object AlreadyExists        extends ThingCreateResponse
  }

  case class Error(message: String)
}
