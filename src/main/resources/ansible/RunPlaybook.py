#
# THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
# FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
#

import sys
from com.xebialabs.xlrelease.plugin.ansible import remoteAnsible


if address == None :
     task.pythonScript.setProperty("address", host.get("address") )
     address = host.get("address")
# End if
if username == None :
     task.pythonScript.setProperty("username", host.get("username") )
     username = host.get("username")
# End if
if password == None :
     task.pythonScript.setProperty("password", host.get("password") )
     password = host.get("password")
# End if
if connectionType == None :
    task.pythonScript.setProperty("connectionType", host.get("connectionType"))
# End if
task.pythonScript.setProperty("protocol", host.get("protocol"))
protocol = host.get("protocol")

envList = "# Setup Environment Variables"
for key in envVars:
    print " %s = %s " % (key, envVars[key])
    envList = "%s\nexport %s=%s" % ( envList, key, envVars[key] )
#End for

print "HOST     %s" % address
print "USERNAME %s" % username
print "-------------------------"
script = """#!/bin/bash
set -x
%s
ansible-playbook %s""" % (envList, playbook)
print script
print "-------------------------"

script = remoteAnsible( task.pythonScript, script)
exitCode = script.execute()

output = script.getStdout()
err = script.getStderr()

if (exitCode == 0):
    print output
else:
    print "Exit code "
    print exitCode
    print
    print "#### Output:"
    print output

    print "#### Error stream:"
    print err
    print
    print "----"

sys.exit(exitCode)

