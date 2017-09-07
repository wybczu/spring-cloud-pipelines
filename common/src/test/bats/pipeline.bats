#!/usr/bin/env bats

load 'test_helper/bats-support/load'
load 'test_helper/bats-assert/load'

setup() {
	export ENVIRONMENT="FooBar"
	source "${BATS_TEST_DIRNAME}/../../main/bash/pipeline.sh"
}

@test "lowerCaseEnv should convert ENVIRONMENT var to lower case" {
	run lowerCaseEnv
	assert_success
	assert_output "foobar"
}
