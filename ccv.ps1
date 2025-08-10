function ccv {
    param(
        [string]$arg
    )
    
    # Set environment variables
    $env:ENABLE_BACKGROUND_TASKS = "true"
    $env:FORCE_AUTO_BACKGROUND_TASKS = "true"
    $env:CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC = "true"
    $env:CLAUDE_CODE_ENABLE_UNIFIED_READ_TOOL = "true"
    $env:CLAUDE_BASH_MAINTAIN_PROJECT_WORKING_DIR = "true"
    $env:BASH_MAX_OUTPUT_LENGTH = "50000"
    
    # Build claude arguments
    $claudeArgs = @()
    
    switch ($arg) {
        "-y" {
            $claudeArgs += "--dangerously-skip-permissions"
        }
        "-r" {
            $claudeArgs += "--resume"
        }
        { $_ -in "-ry", "-yr" } {
            $claudeArgs += "--resume", "--dangerously-skip-permissions"
        }
    }
    
    # Execute claude with arguments
    & claude $claudeArgs
}

# Call the function with any arguments passed to the script
ccv $args[0]