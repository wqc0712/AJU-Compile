import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.core.fs.FileSystem
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala._
import org.hkust.RelationType.Payload

object Job {
  def main(args: Array[String]) {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    val params: ParameterTool = ParameterTool.fromArgs(args)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    var executionConfig = env.getConfig
    executionConfig.enableObjectReuse()
    val inputpath = "/Users/tom/lineitem.tbl"
    val outputpath = "/Users/tom/AggrQ6output.csv"
    val inputStream: DataStream[Payload] = getStream(env, inputpath)
    val result = inputStream.keyBy(i => i._3)
      .process(new Q6lineitemProcessFunction())
      .keyBy(i => i._3)
      .process(new Q6AggregateProcessFunction)
      .map(x => (x._4.mkString(", "), x._5.mkString(", ")))
      .writeAsText(outputpath, FileSystem.WriteMode.OVERWRITE)
      .setParallelism(1)
    env.execute("Flink Streaming Scala API Skeleton")
  }

  private def getStream(env: StreamExecutionEnvironment, dataPath: String): DataStream[Payload] = {
    val data = env.readTextFile(dataPath).setParallelism(1)
    val format = new java.text.SimpleDateFormat("yyyy-MM-dd")
    var cnt: Long = 0
    val restDS: DataStream[Payload] = data
      .map(line => {
        val header = line.substring(0, 3)
        val cells: Array[String] = line.substring(3).split("\\|")
        val i = Tuple4(format.parse(cells(10)), cells(5).toDouble, cells(6).toDouble, cells(4).toDouble)
        var relation = ""
        var action = ""
        header match {
          case "+LI" =>
            relation = "Lineitem"
            action = "Insert"
          case "-LI" =>
            relation = "Lineitem"
            action = "Delete"
        }
        cnt = cnt + 1
        Payload(relation, action,
          Tuple2(cells(0).toInt, cells(3).toInt).asInstanceOf[Any],
          Array(i._1, i._2, i._3, i._4),
          Array("L_SHIPDATE", "L_EXTENDEDPRICE", "L_DISCOUNT", "L_QUANTITY"), cnt)
      }).setParallelism(1).filter(x => x._1 != "").setParallelism(1)
    restDS
  }
}