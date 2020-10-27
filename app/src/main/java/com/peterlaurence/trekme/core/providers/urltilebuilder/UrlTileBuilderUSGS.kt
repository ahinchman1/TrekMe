package com.peterlaurence.trekme.core.providers.urltilebuilder

class UrlTileBuilderUSGS : UrlTileBuilder {
    override fun build(level: Int, row: Int, col: Int): String {
        /* Simulate slow network */
        Thread.sleep(5000)
        return "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/WMTS/tile/1.0.0/USGSTopo/default/GoogleMapsCompatible/$level/$row/$col"
    }
}
