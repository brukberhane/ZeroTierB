# ZerotierB verify gate (Android / Kotlin)
# make verify  = lint + unit tests + assembleDebug
# make install-hooks  → lefthook pre-commit runs make verify

GRADLE ?= ./gradlew
# Prefer local.properties sdk.dir; this default matches this host.
export ANDROID_HOME ?= /opt/android-sdk

.PHONY: help lint test build verify clean install-hooks

help:
	@echo "Targets:"
	@echo "  make verify        - lint + unit tests + assembleDebug (CI / lefthook)"
	@echo "  make lint          - :app:lintDebug"
	@echo "  make test          - :app + :core debug unit tests"
	@echo "  make build         - :app:assembleDebug"
	@echo "  make clean         - gradle clean"
	@echo "  make install-hooks - lefthook install (pre-commit → make verify)"

lint:
	$(GRADLE) :app:lintDebug

test:
	$(GRADLE) :app:testDebugUnitTest :core:testDebugUnitTest

build:
	$(GRADLE) :app:assembleDebug

verify: lint test build

clean:
	$(GRADLE) clean

install-hooks:
	lefthook install
