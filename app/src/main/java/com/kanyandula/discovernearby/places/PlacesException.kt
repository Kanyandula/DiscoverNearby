package com.kanyandula.discovernearby.places

/** Domain failures a [PlacesRepository] reports; the use case maps them to UI state (docs/03 §8, §16). */
sealed class PlacesException(message: String) : Exception(message)

class ProviderFailure(message: String = "Place provider failed") : PlacesException(message)

class NetworkUnavailable(message: String = "Network unavailable") : PlacesException(message)
