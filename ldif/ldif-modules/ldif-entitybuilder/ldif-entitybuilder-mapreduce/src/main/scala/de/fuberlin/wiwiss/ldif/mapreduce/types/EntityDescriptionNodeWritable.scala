package de.fuberlin.wiwiss.ldif.mapreduce.types

import java.io.{DataInput, DataOutput}
import org.apache.hadoop.io.{IntWritable, WritableComparable}
import ldif.entity.NodeWritable

class EntityDescriptionNodeWritable (var entityDescriptionID : IntWritable, var node : NodeWritable) extends WritableComparable[EntityDescriptionNodeWritable]{

  def compareTo(other: EntityDescriptionNodeWritable) = {
    entityDescriptionID.compareTo(other.entityDescriptionID) & node.compareTo(other.node)
  }

  def readFields(input: DataInput) {
    entityDescriptionID.readFields(input)
    node.readFields(input)
  }

  def write(output: DataOutput) {
    entityDescriptionID.write(output)
    node.write(output)
  }
}