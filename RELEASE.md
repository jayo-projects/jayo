## Release
* verify the current version you want to release in gradle.properties
* verify you are using SSH with GIT
* use openJDK 21 as project JDK
* do **publish** task
* go to *build/repos/releases* on **core** and **kotlinx-serial**
* remove the "maven-metadata.xml" (and all files in the same directory) for both projects
* zip the **dev** dir with both projects aggregated
* upload manually on https://central.sonatype.com/publishing, use release name *jayo-X.Y.Z*
  * do **publish** , refresh
  * refresh again after several minutes, deployment status must be "PUBLISHED"
* update sample projects with this just released Jayo version, commit them
* do **release** task (for minor release, press Enter for suggested versions : release version = current,
new version = current + 1)
