package starter

fun <T> List<T>.filterOrReturnExistingIfEmpty(predicate: (T) -> Boolean): List<T> {
    val old = this
    val result = this.filter(predicate)

    if(result.isNotEmpty())
        return result

    return old
}