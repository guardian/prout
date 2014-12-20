package lib

import java.io.IOException

import com.squareup.okhttp.{Callback, OkHttpClient, Request, Response}

import scala.concurrent.{Future, Promise}


package object okhttpscala {

  implicit class RickOkHttpClient(client: OkHttpClient) {

    def execute(request: Request): Future[Response] = {
      val p = Promise[Response]()

      client.newCall(request).enqueue(new Callback {
        override def onFailure(request: Request, e: IOException) {
          p.failure(e)
        }

        override def onResponse(response: Response) {
          p.success(response)
        }
      })

      p.future
    }

  }
}
