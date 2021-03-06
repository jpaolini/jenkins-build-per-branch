package com.entagen.jenkins

import java.util.regex.Pattern

class GitApi {
    String gitUrl
    Pattern branchNameFilter = null
    Integer daysSinceLastCommit = 5;
    Boolean disableLastCommit = false;

    public List<String> getBranchNames() {
        String repo = gitUrl.substring(gitUrl.lastIndexOf('/') + 1, gitUrl.lastIndexOf('.git'))
        String command = "sh get-branches.sh ${gitUrl} ${repo}"
        List<String> branchNames = []

        eachResultLine(command) { String line ->
            line = line.replace("\\t", "\t")
            String branchNameRegex = "^.*\torigin/(.*)\$"
            String branchName = line.find(branchNameRegex) { full, branchName -> branchName }
            Boolean selected = passesFilter(branchName) && (disableLastCommit || passesLastCommitDateFilter(line))

            println "${(selected ? '* ' : '')}  ${line}"
            // lines are in the format of: lastCommitDate\torigin/BRANCH_NAME
            // ex: 1471048873\torigin/master
            if (selected) branchNames << branchName
        }

        return branchNames
    }

    public Boolean passesLastCommitDateFilter(String branch) {
        Date lastCommitForBranch = new Date(branch.tokenize()[0].toLong() * 1000)
        Date commitCutoff = new Date() - daysSinceLastCommit
        return lastCommitForBranch.after(commitCutoff)
    }

    public Boolean passesFilter(String branchName) {
        if (!branchName) return false
        if (!branchNameFilter) return true
        Boolean passed = branchName ==~ branchNameFilter
        return passed
    }

    // assumes all commands are "safe", if we implement any destructive git commands, we'd want to separate those out for a dry-run
    public void eachResultLine(String command, Closure closure) {
        println "executing command: $command"
        def process = command.execute()
        def inputStream = process.getInputStream()
        def gitOutput = ""

        while(true) {
            int readByte = inputStream.read()
            if (readByte == -1) break // EOF
            byte[] bytes = new byte[1]
            bytes[0] = readByte
            gitOutput = gitOutput.concat(new String(bytes))
        }
        process.waitFor()

        if (process.exitValue() == 0) {
            gitOutput.eachLine { String line ->
                closure(line)
            }
        } else {
            String errorText = process.errorStream.text?.trim()
            println "error executing command: $command"
            println errorText
            throw new Exception("Error executing command: $command -> $errorText")
        }
    }

}