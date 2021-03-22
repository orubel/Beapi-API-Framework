package net.nosegrind.apiframework

//@ToString(includeNames = true, includeFields = true)
class AuthenticationToken implements Serializable{

    static mapWith = "mongo"

    String tokenValue
    String username
    Date dateCreated

    static mapping = {
        cache true
        version false
    }

}
