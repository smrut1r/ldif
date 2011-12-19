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

package ldif.modules.silk.hadoop

import org.apache.hadoop.mapreduce._
import ldif.entity.EntityWritable
import lib.input.{FileSplit, SequenceFileInputFormat}
import scala.collection.JavaConversions._
import org.apache.hadoop.io.{Writable, BooleanWritable}
import java.io.{DataInput, DataOutput}

class EntityPairInputFormat extends InputFormat[BooleanWritable, EntityPairWritable] {
  
  type EntitySequenceFileInput = SequenceFileInputFormat[IndexWritable, EntityWritable]

  private val inputFormat = new EntitySequenceFileInput()
  
  override def getSplits(context : JobContext) : java.util.List[InputSplit] = {
    context.getConfiguration.set("mapred.input.dir", context.getConfiguration.get("sourcePath"))
    val sourceSplits = inputFormat.getSplits(context)

    context.getConfiguration.set("mapred.input.dir", context.getConfiguration.get("targetPath"))
    val targetSplits = inputFormat.getSplits(context)

    for(s <- sourceSplits; t <- targetSplits) yield new EntityPairSplit(s, t)
  }

  override def createRecordReader(inputSplit : InputSplit, context : TaskAttemptContext) : RecordReader[BooleanWritable, EntityPairWritable] = {
    new EntityPairReader(inputSplit.asInstanceOf[EntityPairSplit], context)
  }
  
  private class EntityPairReader(split: EntityPairSplit, taskContext: TaskAttemptContext) extends RecordReader[BooleanWritable, EntityPairWritable] {
    
    private var sourceReader: RecordReader[IndexWritable, EntityWritable] = null
    
    private var targetReader: RecordReader[IndexWritable, EntityWritable] = null
    
    override def getProgress = targetReader.getProgress

    override def initialize(inputSplit: InputSplit, context: TaskAttemptContext) {
      sourceReader = inputFormat.createRecordReader(split.sourceSplit, taskContext)
      targetReader = inputFormat.createRecordReader(split.targetSplit, taskContext)

      sourceReader.initialize(split.sourceSplit, taskContext)
      targetReader.initialize(split.targetSplit, taskContext)

      targetReader.nextKeyValue()

      //context.setStatus("Comparing partition " + ...)
    }

    override def close() {
      sourceReader = null
      targetReader = null
    }

    override def nextKeyValue: Boolean = {
      if(sourceReader.nextKeyValue()) {
        true
      }
      else if(targetReader.nextKeyValue()) {
        sourceReader.close()
        sourceReader = inputFormat.createRecordReader(split.sourceSplit, taskContext)
        sourceReader.initialize(split.sourceSplit, taskContext)
        sourceReader.nextKeyValue()
        true
      }
      else {
        false
      }
    }

    override def getCurrentKey = new BooleanWritable(!(sourceReader.getCurrentKey.indices intersect targetReader.getCurrentKey.indices).isEmpty)

    override def getCurrentValue = new EntityPairWritable(sourceReader.getCurrentValue, targetReader.getCurrentValue)
  }
}

class EntityPairSplit(var sourceSplit: InputSplit, var targetSplit: InputSplit) extends InputSplit with Writable {

  def this() = this(new FileSplit(null, 0, 0, null), new FileSplit(null, 0, 0, null))

  override def getLength: Long = sourceSplit.getLength + targetSplit.getLength

  override def getLocations: Array[String] = sourceSplit.getLocations ++ targetSplit.getLocations

  override def write(out : DataOutput) {
    sourceSplit.asInstanceOf[Writable].write(out)
    targetSplit.asInstanceOf[Writable].write(out)
  }

  override def readFields(in : DataInput) {
    sourceSplit.asInstanceOf[Writable].readFields(in)
    targetSplit.asInstanceOf[Writable].readFields(in)
  }
}