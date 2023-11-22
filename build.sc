import mill._, scalalib._
import coursier.maven.MavenRepository

object ivys {
  val scala = "2.13.10"
  val chisel = (ivy"edu.berkeley.cs::chisel3:3.6.0", ivy"edu.berkeley.cs:::chisel3-plugin:3.6.0")
}

trait CommonModule extends ScalaModule {
  override def scalaVersion = ivys.scala

  override def scalacOptions = Seq(
    "-Ymacro-annotations",
    "-language:reflectiveCalls",
    "-feature",
    "-Xcheckinit",
  )
}

trait HasChisel extends ScalaModule {
  override def repositoriesTask = T.task {
    super.repositoriesTask() ++ Seq(
      MavenRepository("https://oss.sonatype.org/content/repositories/snapshots")
    )
  }
  override def ivyDeps = Agg(ivys.chisel._1)
  override def scalacPluginIvyDeps = Agg(ivys.chisel._2)
}

trait HasChiselTests extends SbtModule {
  object test extends SbtModuleTests with TestModule.ScalaTest {
    override def ivyDeps = Agg(ivy"edu.berkeley.cs::chiseltest:0.5.4")
  }
}

trait CommonNOOP extends SbtModule with CommonModule with HasChisel

object difftest extends CommonNOOP {
  override def millSourcePath = os.pwd / "difftest"
}

object playground extends CommonNOOP with HasChiselTests {
  def sources = T.sources {
    super.sources() ++ Seq(PathRef(build.millSourcePath / "playground"))
  }

  override def moduleDeps = super.moduleDeps ++ Seq(
    difftest
  )
}
