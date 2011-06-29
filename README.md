# clj-oauth2

This library implements the OAuth 2.0 protocol for the Clojure
programming language. Currently, only the client side is implemented
and of that only the "Authorization Code" grant type. It aims to
comply with the future [RFC which is currently in draft
status](http://tools.ietf.org/html/draft-ietf-oauth-v2-12) but also
contains work-arounds for popular but non-compliant OAuth 2.0
implementations such as Facebook to be practical.

clj-oauth2 wraps clj-http for accessing protected resources.

## Usage

    (:require [clj-oauth2.client :as oauth2])

    (def facebook-oauth2
      {:client-id "1234567890"
       :client-secret "0987654321"
       :redirect-uri "http://example.com/oauth2-callback"
       :scope ["user_photos" "friends_photos"]
       :authorization-uri "https://graph.facebook.com/oauth/authorize"
       :access-token-uri "https://graph.facebook.com/oauth/access_token"
       :grant-type 'authorization-code})

    ;; redirect user to (:uri auth-req) afterwards
    (def auth-req
      (oauth2/make-auth-request facebook-oauth2 "some-csrf-protection-string"))


    ;; auth-resp contains the query parameters added to the redirect-uri
    ;; by the authorization server
    (def access-token
      (oauth2/get-access-token facebook-oauth2 auth-resp auth-req))

    ;; access protected resource
    (oauth2/get access-token "https://graph.facebook.com/me")

## License

Copyright (c) 2011, Moritz Heidkamp
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
