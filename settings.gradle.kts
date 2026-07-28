pluginManagement {
    repositories {
        // 国内镜像 - 阿里云（优先）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        // 腾讯云镜像作为备选
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        // 官方仓库作为最后兜底
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 国内镜像 - 阿里云（优先）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 腾讯云镜像作为备选
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        // JitPack 仓库
        maven { url = uri("https://jitpack.io") }
        // 官方仓库作为最后兜底
        google()
        mavenCentral()
    }
}

rootProject.name = "MtDownloader"
include(":app")
