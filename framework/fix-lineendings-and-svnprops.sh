#!/bin/bash
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#  
#         http://www.apache.org/licenses/LICENSE-2.0
#         
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.

TMP_NATIVE_FILE=/tmp/`basename $0`.native.$$
TMP_MIMETYPE_FILE=/tmp/`basename $0`.mimetype.$$

echo "pom.xml
INSTALL
KEYS
LICENSE
NOTICE
README
*.java
*.apt 
*.bat
*.cmd
*.dtd
*.groovy
*.html
*.jsp
*.js
*.properties
*.sql
*.txt
*.xhtml
*.xml" >$TMP_NATIVE_FILE

echo "*.png image/png
*.doc application/msword
*.docx application/vnd.openxmlformats-officedocument.wordprocessingml.document
*.gif image/gif
*.ico image/x-icon
*.jpg image/jpeg
*.tiff image/tiff
*.pdf application/pdf
*.rtf application/rtf
*.odp  vnd.oasis.opendocument.presentation
*.pptx application/vnd.openxmlformats-officedocument.presentationml.presentation
*.xslx application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
" >$TMP_MIMETYPE_FILE

while read a; do
	/usr/bin/find . -name "$a" -exec dos2unix {} \;
	/usr/bin/find . -name "$a" -exec svn ps svn:eol-style native {} \;
done < $TMP_NATIVE_FILE

while read a; do
	mimetype=`echo $a | awk '{print $1}'`
	extension=`echo $a | awk '{print $2}'`
	echo "$mimetype $extension"
	/usr/bin/find . -name "$extension" -exec svn ps svn:mime-type "$mimetype" {} \;
done < $TMP_MIMETYPE_FILE




rm $TMP_NATIVE_FILE
rm $TMP_MIMETYPE_FILE
