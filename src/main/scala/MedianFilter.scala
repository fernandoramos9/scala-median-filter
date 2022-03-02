import java.awt.image.BufferedImage
import java.io.File

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import javax.imageio.ImageIO

import scala.collection.parallel.CollectionConverters.ImmutableIterableIsParallelizable
import scala.concurrent.Await
import scala.concurrent.duration._

import scala.language.postfixOps

class serialServer extends Actor{
  def receive : PartialFunction[Any, Unit]  = {
    case Serial(inputImage) => {
      val startTime = System.currentTimeMillis()
      val filteredImage = serialFilter(inputImage)
      val endTime = System.currentTimeMillis() - startTime
      sender() ! (endTime, filteredImage)
    }
      def serialFilter(inputImage: BufferedImage): BufferedImage = {
        val width: Int = inputImage.getWidth()
        val height: Int = inputImage.getHeight()

        //Apply filter
        val window = new Array[Int](9)
        val filteredImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        for(rows <- 1 until height-1){ //Due to the nature of the algorithm borders are ignored.
          for(columns <- 1 until width-1){

            //Neighbor pixels are taken to make the analysis

            window(0) = inputImage.getRGB(columns - 1, rows - 1)
            window(1) = inputImage.getRGB(columns - 1, rows)
            window(2) = inputImage.getRGB(columns - 1, rows + 1)

            window(3) = inputImage.getRGB(columns, rows - 1)
            window(4) = inputImage.getRGB(columns, rows)
            window(5) = inputImage.getRGB(columns, rows + 1)

            window(6) = inputImage.getRGB(columns + 1, rows - 1)
            window(7) = inputImage.getRGB(columns + 1, rows)
            window(8) = inputImage.getRGB(columns + 1, rows + 1)

            val sortedWindow = window.sorted
            filteredImage.setRGB(columns, rows, sortedWindow(4)) //sortedWindow(4) will always be the median

          }
        }
        filteredImage
      }
  }
}

class parallelServer extends Actor{

  def receive: PartialFunction[Any, Unit]= {
    case Parallel(inputImage) =>{
      val startTime = System.currentTimeMillis()
      val filteredImage = parallelFilter(inputImage)
      val endTime = System.currentTimeMillis() - startTime
      sender() ! (endTime, filteredImage)
    }

      def parallelFilter(inputImage: BufferedImage): BufferedImage ={
        val width = inputImage.getWidth()
        val height = inputImage.getHeight()

        val widthRange = 1 to width-2 par //This makes a ParRange
        val heightRange = 1 to height-2 par


        val filteredImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        widthRange.foreach(x => median(x)) //Iterates through all elements in the x dir in parallel

        def median(columns: Int): Unit = {
          heightRange.foreach {  //Iterates through all elements in the y dir in parallel

            rows =>

            val window = new Array[Int](9)

            window(0) = inputImage.getRGB(columns - 1, rows - 1)
            window(1) = inputImage.getRGB(columns - 1, rows)
            window(2) = inputImage.getRGB(columns - 1, rows + 1)

            window(3) = inputImage.getRGB(columns, rows - 1)
            window(4) = inputImage.getRGB(columns, rows)
            window(5) = inputImage.getRGB(columns, rows + 1)

            window(6) = inputImage.getRGB(columns + 1, rows - 1)
            window(7) = inputImage.getRGB(columns + 1, rows)
            window(8) = inputImage.getRGB(columns + 1, rows + 1)

            val sortedWindow = window.sorted
            filteredImage.setRGB(columns, rows, sortedWindow(4)) //sortedWindow(4) will always be the median)
          }
        }
        filteredImage
      }

  }

}


object MedianFilter extends App{
  println("(Type men.png or flowers.jpeg for testing images provided, also be sure to included all images on the Images folder)")
  val imagePath = scala.io.StdIn.readLine("Enter the path of the image you want to filter:  ")
  val inputImage = ImageIO.read(new File("Images/" + imagePath))
  implicit val timeout: Timeout = 20.seconds
  
  // System 1
  val actorSystem = ActorSystem("ActorSystem")
  val serialActor = actorSystem.actorOf(Props[serialServer], name = "serialActor")
  val serialFuture = serialActor ? Serial(inputImage)

  // System 2
  val actorSystem2 = ActorSystem("ActorSystem")
  val parallelActor = actorSystem2.actorOf(Props[parallelServer], name = "parallelActor")
  val parallelFuture = parallelActor ? Parallel(inputImage)

  // Results
  val serialResult = Await.result(serialFuture,Duration(6000,"millis")).asInstanceOf[(Long, BufferedImage)]

  ImageIO.write(serialResult._2, "jpg", new File("output/serialImage.jpg"))

  val parallelResult = Await.result(parallelFuture,Duration(6000,"millis")).asInstanceOf[(Long, BufferedImage)]
  ImageIO.write(parallelResult._2, "jpg", new File("output/parallelImage.jpg"))

  // print outputs
  println("Serial Time: " + serialResult._1)
  println("Parallel Time: " + parallelResult._1)
  println("Results will be on the output folder!")
  
  actorSystem.terminate()
  actorSystem2.terminate()
}

case class Serial(inputImage: BufferedImage)

case class Parallel(inputImage: BufferedImage)