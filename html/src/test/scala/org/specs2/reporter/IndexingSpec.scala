package org.specs2
package reporter

import fp.syntax.*
import io.*
import FileName.*
import matcher.OperationMatchers.*
import html.*
import Indexing.*
import control.*
import producer.*, Producer.*
import specification.core.Env
import org.specs2.concurrent.ExecutionEnv

class IndexingSpec(using ee: ExecutionEnv) extends Specification {
  def is = s2"""
 From the set of all the generated html pages we can generate an index and convert it to the tipue search format.

 An index is built from Html pages     $index
 The index can be saved to a Json file $save

"""

  def index = html.Index.createIndex(pages(0)) must ===(
    html.Index(
      Vector(
        IndexEntry(
          title = "page 1",
          text = """test 'hello world'""",
          tags = Vector("tag1", "tag2"),
          path = FilePath("page1")
        )
      )
    )
  )

  def save =
    val path = "target" / "test" / "IndexingSpec" | "index.js"
    emitSeq[Action, IndexedPage](pages).fold(indexFold(path).into[Action]).runAction(ee)

    val expected =
      s"""|var tipuesearch = {'pages': [{'title':'page 1', 'text':'test hello world', 'tags':'tag1 tag2', 'loc':'page1'},
          |{'title':'page 2', 'text':'content2', 'tags':'tag3', 'loc':'page2'}]};""".stripMargin

    FileSystem(NoLogger).readFile(path).map(_.trim) must beOk(===(expected))

  val pages = Vector(
    IndexedPage(FilePath("page1"), "page 1", """test 'hello world'""", Vector("tag1", "tag2")),
    IndexedPage(FilePath("page2"), "page 2", """content2""", Vector("tag3"))
  )

}
