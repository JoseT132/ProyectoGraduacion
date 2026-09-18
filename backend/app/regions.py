"""Regiones biogeográficas gruesas para verificar plausibilidad de detecciones.

Cada región es un bounding box (lat_min, lat_max, lng_min, lng_max).
Son aproximaciones para plausibilidad general, no límites biogeográficos exactos.
"""

REGIONS = {
    "Centroamérica y Caribe": (5.0, 25.0, -95.0, -58.0),
    "Norteamérica": (15.0, 72.0, -170.0, -50.0),
    "Sudamérica": (-56.0, 13.0, -82.0, -30.0),
    "Europa": (35.0, 72.0, -25.0, 60.0),
    "África": (-35.0, 38.0, -20.0, 55.0),
    "Asia": (-10.0, 82.0, 55.0, 180.0),
    "Oceanía": (-50.0, 0.0, 110.0, 180.0),
}

COSMOPOLITAN = "Cosmopolita"


def regions_for(latitude: float, longitude: float):
    """Devuelve la lista de regiones cuyo bounding box contiene el punto."""
    matched = []
    for name, (lat_min, lat_max, lng_min, lng_max) in REGIONS.items():
        if lat_min <= latitude <= lat_max and lng_min <= longitude <= lng_max:
            matched.append(name)
    return matched


def in_distribution_range(distribution, latitude: float, longitude: float):
    """¿Las coordenadas caen dentro del rango conocido de la especie?

    distribution: lista de nombres de región o ["Cosmopolita"].
    Retorna (in_range, matched_regions).
    """
    if not distribution:
        return None, regions_for(latitude, longitude)
    if COSMOPOLITAN in distribution:
        return True, regions_for(latitude, longitude)
    matched = regions_for(latitude, longitude)
    return any(r in distribution for r in matched), matched
