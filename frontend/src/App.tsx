import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import Signup from "./Signup";
import Login from "./Login";
import Welcome from "./Welcome";
import Home from "./Home";
import RideRequest from "./RideRequest";
import "./App.css";
import RideMatches from './RideMatches';
import ActiveRide from './ActiveRide';
import ParentSignup from './ParentSignup';
import ParentLogin from './ParentLogin';
import ParentHome from './ParentHome';
import FeedbackPage from "./FeedbackPage";
function App() {
  return (
    <Router>
      <Routes>
        <Route path="/ride/:rideId/feedback" element={<FeedbackPage />} />
        <Route path="/ride/active" element={<ActiveRide />} />
        <Route path="/" element={<Login />} />
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<Signup />} />
        <Route path="/welcome" element={<Welcome />} />
        <Route path="/home" element={<Home />} />
        <Route path="/ride/request" element={<RideRequest />} />
        <Route path="/ride/matches" element={<RideMatches />} />
        <Route path="/ride/matches/:rideId" element={<RideMatches />} />
        <Route path="/parent/signup" element={<ParentSignup />} />
        <Route path="/parent/login" element={<ParentLogin />} />
        <Route path="/parent/home" element={<ParentHome />} />
        <Route path="*" element={<div>404: Page Not Found</div>} />
      </Routes>
    </Router>
  );
}

export default App;