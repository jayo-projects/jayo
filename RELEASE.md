## Release
* verify the current version you want to release in gradle.properties
* verify you are using SSH with GIT
* use openJDK 21 as project JDK
* do **publish** task
* go to *build/repos/releases* on **core** and **kotlinx-serial**
* remove the "maven-metadata.xml" (and all files in the same directory) for both projects
* zip the **dev** dir with both projects aggregated. Save it with a name like *jayo-0.1.0-Alpha11.zip*.
* upload manually on https://central.sonatype.com/publishing
  * do **Publish Component**
  * Deployment name -> use release name *jayo-X.Y.Z*, use the same name as the zip.
  * Click on **Publish Component**, **Refresh**. Check artifacts are ok, then **Publish**
  * refresh again after several minutes, deployment status must be "PUBLISHED"
* do **release** task (for minor release, press Enter for suggested versions : release version = current,
new version = current + 1)
