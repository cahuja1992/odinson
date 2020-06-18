package ai.lum.odinson.events

import org.scalatest._
import ai.lum.odinson.utils.OdinResultsIterator
import ai.lum.odinson.{EventMatch, OdinsonMatch}

class TestEvents extends EventSpec {

//  import TestEvents._

  val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""

  val pattern = """
    trigger = [lemma=eat]
    subject: NP = >nsubj
    object: NP = >dobj
  """

  // extractor engine persists across tests (hacky way)
  val doc = getDocumentFromJson(json)
  val ee = Utils.mkExtractorEngine(doc)

  "Odinson" should "match event with promoted entities" in {
    val pattern = """
      trigger = [lemma=eat]
      subject: ^NP = >nsubj [chunk=B-NP][chunk=I-NP]*
      object: ^NP = >dobj [chunk=B-NP][chunk=I-NP]*
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      createArgument("subject", 0, 1),
      createArgument("object", 2, 4),
    )
    testEventArguments(m, desiredArgs)
  }

  it should "respect quantifiers in arguments" in {
    val pattern = """
      trigger = [lemma=eat]
      subject: ^NP = >nsubj [chunk=B-NP][chunk=I-NP]*
      object: ^NP = >dobj gummy? bears
    """
    // the above rule should match {bears} and {gummy bears}
    // and then keep only {gummy bears} because the quantifier `?` is greedy
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      createArgument("subject", 0, 1),
      createArgument("object", 2, 4),
    )
    testEventArguments(m, desiredArgs)
  }

  it should "promote a token when no surface pattern is provided" in {
    val pattern = """
      trigger = [lemma=eat]
      subject: ^NP = >nsubj
      object: ^NP = >dobj
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      createArgument("subject", 0, 1),
      createArgument("object", 3, 4),
    )
    testEventArguments(m, desiredArgs)
  }

  it should "match when no label is provided" in {
    val pattern = """
      trigger = [lemma=eat]
      subject = >nsubj [chunk=B-NP][chunk=I-NP]*
      object = >dobj [chunk=B-NP][chunk=I-NP]*
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      createArgument("subject", 0, 1),
      createArgument("object", 2, 4),
    )
    testEventArguments(m, desiredArgs)
  }

  it should "promote a token when no surface pattern is provided and label is not provided" in {
    val pattern = """
      trigger = [lemma=eat]
      subject = >nsubj
      object = >dobj
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      createArgument("subject", 0, 1),
      createArgument("object", 3, 4),
    )
    testEventArguments(m, desiredArgs)
  }

  it should "not throw an exception when it fails" in {
    val pattern = """
      trigger = [lemma=eat]
      subject: ^NP = >nsubj xxx
      object: ^NP = >dobj yyy
    """
    val q = ee.compiler.compileEventQuery(pattern)
    noException should be thrownBy ee.query(q, 1)
  }

  it should "not find event with mentions from the state when the state is empty" in {
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (0)
  }

  it should "populate the state with NPs" in {
    val query = ee.compiler.mkQuery("[chunk=B-NP][chunk=I-NP]*")
    val results = ee.query(query)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 2
    ee.state.addMentions(OdinResultsIterator(results, "NP"))
  }

  it should "find event with mentions from the state when the state is populated" in {
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      createArgument("subject", 0, 1),
      createArgument("object", 2, 4),
    )
    testEventArguments(m, desiredArgs)
  }
}


