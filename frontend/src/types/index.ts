export interface RideResponse {
    id: number;
    pickupLat: number;
    pickupLon: number;
    dropoffLat: number;
    dropoffLon: number;
    pickupAddress: string;
    dropoffAddress: string;
    status: string;
    carbonEstimate: number;
    h3Index: string;
    userId: number;
    currentLat?: number;
    currentLon?: number;
}

export interface UserProfile {
    id: number;
    username: string;
    email?: string;
    trustScore: number;
    profilePictureUrl: string;
}

export interface RideMatchRequestResponse {
    id: number;
    fromRideId: number;
    toRideId: number;
    status: string;
}

export interface Location {
    lat: number;
    lon: number;
    address: string;
}

export interface RideMatchResponse {
    rideId: number;
    matchedRideIds: number[];
    clusterId: number;
}
