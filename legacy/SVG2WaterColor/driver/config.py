# Physics Configuration

# Pen Heights (0-100%)
# UP: Clearance height for moving without drawing
# DOWN: Drawing height
PEN_HEIGHTS = {
    "UP": 60,
    "DOWN": 30
}

# Station Configuration
# Coordinates in mm relative to Home (0,0)
# behavior: 'dip_swirl' (complex refill) or 'simple_dip' (quick refill)
STATIONS = {
    "default_station": { 
        "x": 5.0, 
        "y": 50.0, 
        "z_down": 20, # Deeper dip for refill 
        "behavior": "simple_dip" 
    },
    "red_wash": { 
        "x": 10.0, 
        "y": 100.0, 
        "z_down": 10, 
        "behavior": "dip_swirl" 
    },
    "blue_wash": { 
        "x": 30.0, 
        "y": 100.0, 
        "z_down": 10, 
        "behavior": "simple_dip" 
    }
}
