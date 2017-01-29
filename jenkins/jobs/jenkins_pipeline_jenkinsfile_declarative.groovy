import javaposse.jobdsl.dsl.DslFactory
import javaposse.jobdsl.dsl.helpers.BuildParametersContext

/*
	TODO: TO develop
	- write bash tests
	- perform blue green deployment
	- implement the complete step
*/

DslFactory dsl = this

// These will be taken either from seed or global variables
PipelineDefaults defaults = new PipelineDefaults(binding.variables)

// Example of a version with date and time in the name
String pipelineVersion = binding.variables["PIPELINE_VERSION"] ?: '''1.0.0.M1-${GROOVY,script ="new Date().format('yyMMdd_HHmmss')"}-VERSION'''
String cronValue = "H H * * 7" //every Sunday - I guess you should run it more often ;)
String testReports = ["**/surefire-reports/*.xml", "**/test-results/**/*.xml"].join(",")
String gitCredentials = binding.variables["GIT_CREDENTIAL_ID"] ?: "git"
String jdkVersion = binding.variables["JDK_VERSION"] ?: "jdk8"
String cfTestCredentialId = binding.variables["CF_TEST_CREDENTIAL_ID"] ?: "cf-test"
String cfStageCredentialId = binding.variables["CF_STAGE_CREDENTIAL_ID"] ?: "cf-stage"
String cfProdCredentialId = binding.variables["CF_PROD_CREDENTIAL_ID"] ?: "cf-prod"
String gitEmail = binding.variables["GIT_EMAIL"] ?: "pivo@tal.com"
String gitName = binding.variables["GIT_NAME"] ?: "Pivo Tal"
boolean autoStage = binding.variables["AUTO_DEPLOY_TO_STAGE"] == null ? false : Boolean.parseBoolean(binding.variables["AUTO_DEPLOY_TO_STAGE"])
boolean autoProd = binding.variables["AUTO_DEPLOY_TO_PROD"] == null ? false : Boolean.parseBoolean(binding.variables["AUTO_DEPLOY_TO_PROD"])
boolean rollbackStep = binding.variables["ROLLBACK_STEP_REQUIRED"] == null ? false : Boolean.parseBoolean(binding.variables["ROLLBACK_STEP_REQUIRED"])
String scriptsDir = binding.variables["SCRIPTS_DIR"] ?: "${WORKSPACE}/common/src/main/bash"

// we're parsing the REPOS parameter to retrieve list of repos to build
String repos = binding.variables["REPOS"] ?:
		["https://github.com/marcingrzejszczak/github-analytics",
		 "https://github.com/marcingrzejszczak/github-webhook"].join(",")
List<String> parsedRepos = repos.split(",")
parsedRepos.each {
	List<String> parsedEntry = it.split('\\$')
	String gitRepoName
	String fullGitRepo
	if (parsedEntry.size() > 1) {
		gitRepoName = parsedEntry[0]
		fullGitRepo = parsedEntry[1]
	} else {
		gitRepoName = parsedEntry[0].split('/').last()
		fullGitRepo = parsedEntry[0]
	}

	dsl.pipelineJob("${gitRepoName}-declarative-pipeline") {
		triggers {
			cron(cronValue)
			githubPush()
		}
		wrappers {
			environmentVariables {
				environmentVariables(defaults.defaultEnvVars)
			}
			parameters(PipelineDefaults.defaultParams())
			timestamps()
			colorizeOutput()
			maskPasswords()
			timeout {
				noActivity(300)
				failBuild()
				writeDescription('Build failed due to timeout after {0} minutes of inactivity')
			}
		}
		jdk(jdkVersion)
		definition {
			cps {
				script(dsl.readFileFromWorkspace("${WORKSPACE}/jenkins/pipeline-model/Jenkinsfile"))
			}
		}
	}
}

/**
 * A helper class to provide delegation for Closures. That way your IDE will help you in defining parameters.
 * Also it contains the default env vars setting
 */
class PipelineDefaults {

	final Map<String, String> defaultEnvVars

	PipelineDefaults(Map<String, String> variables) {
		this.defaultEnvVars = defaultEnvVars(variables)
	}

	private Map<String, String> defaultEnvVars(Map<String, String> variables) {
		Map<String, String> envs = [:]
		envs['CF_TEST_API_URL'] = variables['CF_TEST_API_URL'] ?: 'api.local.pcfdev.io'
		envs['CF_STAGE_API_URL'] = variables['CF_STAGE_API_URL'] ?: 'api.local.pcfdev.io'
		envs['CF_PROD_API_URL'] = variables['CF_PROD_API_URL'] ?: 'api.local.pcfdev.io'
		envs['CF_TEST_ORG'] = variables['CF_TEST_ORG'] ?: 'pcfdev-org'
		envs['CF_TEST_SPACE'] = variables['CF_TEST_SPACE'] ?: 'pfcdev-test'
		envs['CF_STAGE_ORG'] = variables['CF_STAGE_ORG'] ?: 'pcfdev-org'
		envs['CF_STAGE_SPACE'] = variables['CF_STAGE_SPACE'] ?: 'pfcdev-stage'
		envs['CF_PROD_ORG'] = variables['CF_PROD_ORG'] ?: 'pcfdev-org'
		envs['CF_PROD_SPACE'] = variables['CF_PROD_SPACE'] ?: 'pfcdev-prod'
		envs['CF_HOSTNAME_UUID'] = variables['CF_HOSTNAME_UUID'] ?: ''
		envs['M2_SETTINGS_REPO_ID'] = variables['M2_SETTINGS_REPO_ID'] ?: 'artifactory-local'
		envs['REPO_WITH_JARS'] = variables['REPO_WITH_JARS'] ?: 'http://artifactory:8081/artifactory/libs-release-local'
		return envs
	}

	public static final String groovyEnvScript = '''
String workspace = binding.variables['WORKSPACE']
String mvn = "${workspace}/mvnw"
String gradle =  "${workspace}/gradlew"

Map envs = [:]
if (new File(mvn).exists()) {
	envs['PROJECT_TYPE'] = "MAVEN"
	envs['OUTPUT_FOLDER'] = "target"
} else if (new File(gradle).exists()) {
	envs['PROJECT_TYPE'] = "GRADLE"
	envs['OUTPUT_FOLDER'] = "build/libs"
}
return envs'''

	protected static Closure context(@DelegatesTo(BuildParametersContext) Closure params) {
		params.resolveStrategy = Closure.DELEGATE_FIRST
		return params
	}

	/**
	 * With the Security constraints in Jenkins in order to pass the parameters between jobs, every job
	 * has to define the parameters on input. In order not to copy paste the params we're doing this
	 * default params method.
	 */
	static Closure defaultParams() {
		return context {
			booleanParam('REDOWNLOAD_INFRA', false, "If Eureka & StubRunner & CF binaries should be redownloaded if already present")
			booleanParam('REDEPLOY_INFRA', true, "If Eureka & StubRunner binaries should be redeployed if already present")
			stringParam('EUREKA_GROUP_ID', 'com.example.eureka', "Group Id for Eureka used by tests")
			stringParam('EUREKA_ARTIFACT_ID', 'github-eureka', "Artifact Id for Eureka used by tests")
			stringParam('EUREKA_VERSION', '0.0.1.M1', "Artifact Version for Eureka used by tests")
			stringParam('STUBRUNNER_GROUP_ID', 'com.example.github', "Group Id for Stub Runner used by tests")
			stringParam('STUBRUNNER_ARTIFACT_ID', 'github-analytics-stub-runner-boot', "Artifact Id for Stub Runner used by tests")
			stringParam('STUBRUNNER_VERSION', '0.0.1.M1', "Artifact Version for Stub Runner used by tests")
		}
	}

	/**
	 * With the Security constraints in Jenkins in order to pass the parameters between jobs, every job
	 * has to define the parameters on input. We provide additional smoke tests parameters.
	 */
	static Closure smokeTestParams() {
		return context {
			stringParam('APPLICATION_URL', '', "URL of the deployed application")
			stringParam('STUBRUNNER_URL', '', "URL of the deployed stub runner application")
		}
	}
}
