package com.swarmnyc.fulton.android.identity

import com.swarmnyc.fulton.android.Fulton
import com.swarmnyc.fulton.android.FultonContext
import com.swarmnyc.fulton.android.http.ApiOneResult
import com.swarmnyc.fulton.android.http.FultonApiClient
import com.swarmnyc.fulton.android.http.Method
import com.swarmnyc.fulton.android.http.Request
import com.swarmnyc.promisekt.Promise

/**
 * the implementation of Account Api Client
 */
abstract class IdentityApiClient<T : User>(context: FultonContext = Fulton.context)  : FultonApiClient(context) {
    fun signIn(username: String, password: String): Promise<AccessToken> {
        return request {
            method = Method.POST
            paths("login")
            body = mapOf("username" to username, "password" to password)
        }
    }

    fun signUp(username: String, email: String, password: String): Promise<AccessToken> {
        return request {
            method = Method.POST
            paths("register")
            body = mapOf("username" to username, "email" to email, "password" to password)
        }
    }

    fun googleSignIn(code: String): Promise<AccessToken> {
        return request {
            paths("google/callback")
            query("code" to code, "noRedirectUrl" to "true")
        }
    }

    fun facebookSignIn(code: String): Promise<AccessToken> {
        return request {
            paths("facebook/callback")
            query("access_token" to code)
        }
    }

    fun oauthSignIn(provider: String, vararg params: Pair<String, String>): Promise<AccessToken> {
        return request {
            paths("$provider/callback")
            query(*params)
        }
    }

    fun profile(): Promise<T> {
        val req = Request().apply {
            resultType = context.userType
            paths("profile")
        }

        return request(req)
    }

    fun updateProfile(user: T): Promise<Unit> {
        return request {
            method = Method.POST
            paths("profile")
            body = mapOf("data" to user)
        }
    }

    fun forgotPassword(email: String): Promise<ForgotPasswordResult> {
        return request {
            method = Method.POST
            resultType = ApiOneResult::class.java
            resultTypeGenerics(ForgotPasswordResult::class.java)
            paths("forgot-password")
            body = mapOf("email" to email)
        }
    }

    fun verifyResetPasswordCode(token: String, code: String): Promise<Unit> {
        return request {
            method = Method.POST
            paths("verify-reset-password")
            body = mapOf("token" to token, "code" to code)
        }
    }

    fun resetPassword(token: String, code: String, password: String): Promise<Unit> {
        return request {
            method = Method.POST
            paths("reset-password")
            body = mapOf("token" to token, "code" to code, "password" to password)
        }
    }

    fun changePassword(oldPassword: String, newPassword: String): Promise<Unit> {
        return request {
            method = Method.POST
            paths("change-password")
            body = mapOf("oldPasswordoldPassword" to oldPassword, "newPassword" to newPassword)
        }
    }

    fun signOut(): Promise<Unit> {
        return request {
            paths("logout")
        }
    }
}