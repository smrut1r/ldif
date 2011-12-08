/* 
 * LDIF
 *
 * Copyright 2011 Freie Universität Berlin, MediaEvent Services GmbH & Co. KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ldif.hadoop.entitybuilder.phases

import ldif.hadoop.entitybuilder.mappers._
import org.apache.hadoop.mapred._
import lib.{NullOutputFormat, MultipleOutputs}
import org.apache.hadoop.util._
import org.apache.hadoop.conf._
import ldif.hadoop.types._
import ldif.hadoop.entitybuilder.io._
import ldif.hadoop.utils.HadoopHelper
import ldif.entity.{EntityDescription, EntityDescriptionMetadata, EntityDescriptionMetaDataExtractor}
import org.slf4j.LoggerFactory
import ldif.hadoop.io.{QuadSequenceFileOutput, QuadSequenceFileInput}
import org.apache.hadoop.io.{NullWritable, IntWritable}
import org.apache.hadoop.fs.{FileSystem, Path}
import ldif.util.Consts

/**
 *  Hadoop EntityBuilder - Phase 2
 *  Filtering quads and creating initial value paths
 **/

class Phase2 extends Configured with Tool {
  def run(args: Array[String]): Int = {
    val conf = getConf
    val job = new JobConf(conf, classOf[Phase2])
    val getsTextInput = args(2).toBoolean

    job.setJobName("HEB-Phase2")

    //    job.setJarByClass(classOf[RunHadoop])
    job.setNumReduceTasks(0)
    if(getsTextInput)
      job.setMapperClass(classOf[ExtractAndProcessQuadsMapper])
    else {
      job.setMapperClass(classOf[ProcessQuadsMapper])
      job.setInputFormat(classOf[QuadSequenceFileInput])
    }
    job.setMapOutputKeyClass(classOf[IntWritable])
    job.setMapOutputValueClass(classOf[ValuePathWritable])
    job.setOutputKeyClass(classOf[IntWritable])
    job.setOutputValueClass(classOf[ValuePathWritable])
    job.setOutputFormat(classOf[NullOutputFormat[IntWritable, ValuePathWritable]])

    MultipleOutputs.addNamedOutput(job, "seq", classOf[JoinValuePathMultipleSequenceFileOutput], classOf[IntWritable], classOf[ValuePathWritable])
    MultipleOutputs.addNamedOutput(job, "text", classOf[JoinValuePathMultipleTextFileOutput], classOf[IntWritable], classOf[ValuePathWritable])
    MultipleOutputs.addNamedOutput(job, "sameas", classOf[QuadSequenceFileOutput], classOf[NullWritable], classOf[QuadWritable])

    val in = new Path(args(0))
    val out = new Path(args(1))
    FileInputFormat.addInputPath(job, in)
    FileOutputFormat.setOutputPath(job, out)

    JobClient.runJob(job)

    return 0
  }
}

object Phase2 {
  private val log = LoggerFactory.getLogger(getClass.getName)

  def runPhase(in : String, out : String, entityDescriptions : Seq[EntityDescription], sameAs : String, getsTextInput: Boolean = false) : Int = {
    val edmd = EntityDescriptionMetaDataExtractor.extract(entityDescriptions)
    runPhase(in,out,edmd, sameAs, getsTextInput)
  }

  def runPhase(in : String, out : String, edmd : EntityDescriptionMetadata, sameAs : String, getsTextInput: Boolean) : Int = {
    log.info("Starting phase 2 of the EntityBuilder: Filtering quads and creating initial value paths")

    val start = System.currentTimeMillis
    val conf = new Configuration
    HadoopHelper.distributeSerializableObject(edmd, conf, "edmd")

    // remove existing output
    val hdfs = FileSystem.get(conf)
    val hdPath = new Path(out)
    if (hdfs.exists(hdPath))
      hdfs.delete(hdPath, true)

    log.info("Output directory: " + out)
    val res = ToolRunner.run(conf, new Phase2(), Array[String](in, out, getsTextInput.toString))

    log.info("That's it. Took " + (System.currentTimeMillis-start)/1000.0 + "s")

    // move sameAs links in the ad-hoc direcotry
    val sameAsOutputFiles = hdfs.listStatus(hdPath).filterNot(_.isDir)
    for (status <- sameAsOutputFiles)
      hdfs.rename(status.getPath, new Path(sameAs+Consts.fileSeparator+status.getPath.getName))

    res
  }
}