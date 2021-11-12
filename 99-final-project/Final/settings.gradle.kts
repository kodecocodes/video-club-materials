dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // Agora RTM SDK is still served through JCenter
        @Suppress("JcenterRepositoryObsolete")
        jcenter()
    }
}

rootProject.name = "Club RW"
include(":app")
include(":agora-ktx")
include(":api")
include(":fake-server")
