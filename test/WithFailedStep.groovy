trait WithFailedStep {
    Closure failStep(String _message) {
        throw new StepFailedException(_message)
    }

    static class StepFailedException extends RuntimeException {
        StepFailedException(String _message) { super(_message) }
    }
}
